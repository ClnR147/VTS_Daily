package com.example.vtsdaily.lookup

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.SortedMap
import java.util.TreeMap

object LookupStore {
    private const val FILE_NAME = "PassengerLookup.json"
    private const val TMP_SUFFIX = ".tmp"
    private val utf8: Charset = Charsets.UTF_8

    private const val TAG = "DateSelect"

    private val EXPECTED_HEADER = listOf(
        "DriveDate","Passenger","A/R","PAddress","DAddress","PUTimeAppt","DOTimeAppt","RTTime","Phone"
    )
    private val REQUIRED_COLS = setOf("DriveDate","Passenger")

    // Your CURRENT date parser (use exactly what your importer uses today)
    private val currentDateFormat = DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US)
    private fun parseDateCurrent(raw: String): LocalDate? =
        try { LocalDate.parse(raw.trim(), currentDateFormat) } catch (_: Exception) { null }

    // (Optional) a tolerant parser used ONLY for diagnostics; acceptance still uses parseDateCurrent
    private val diagFlexFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/uuuu"),
        DateTimeFormatter.ofPattern("M/d/uu"),
        DateTimeFormatter.ofPattern("M-d-uuuu"),
        DateTimeFormatter.ofPattern("M-d-uu"),
        DateTimeFormatter.ofPattern("uuuuMMdd"),
        DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.US),
    )
    private fun parseDateFlex(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) { // Excel serial (diagnostic only)
            return try {
                val serial = s.toLong()
                if (serial in 2..80000) LocalDate.of(1899,12,30).plusDays(serial) else null
            } catch (_: Exception) { null }
        }
        for (f in diagFlexFormats) { try { return LocalDate.parse(s, f) } catch (_: Exception) {} }
        return null
    }

    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    private val TIME_RE = Regex("""\b(\d{1,2}):(\d{2})\b""")

    private fun String.toMinutesForTimeline(): Int? {
        val isReturn = trim().startsWith("PR", ignoreCase = true)
        val matches = TIME_RE.findAll(this).toList()
        val pick = when {
            matches.isEmpty() -> null
            isReturn && matches.size >= 2 -> matches.last()
            else -> matches.first()
        }
        return pick?.destructured?.let { (h, m) -> h.toInt() * 60 + m.toInt() }
    }

    private fun String.firstTimeMinutesOrNull(): Int? =
        TIME_RE.find(this)?.destructured?.let { (h, m) -> h.toInt() * 60 + m.toInt() }

    private fun String.isReturnTag(): Boolean =
        trim().startsWith("PR", ignoreCase = true)

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
    private fun tmpFile(context: Context) = File(context.filesDir, "$FILE_NAME$TMP_SUFFIX")

    fun load(context: Context): List<LookupRow> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val json = f.readText(utf8)
            val type = object : TypeToken<List<LookupRow>>() {}.type
            gson.fromJson<List<LookupRow>>(json, type) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private val dateFormats: List<DateTimeFormatter> = listOf(
        "M/d/yyyy",
        "MM/dd/yyyy",
        "yyyy-MM-dd",
        "MMMM d, yyyy"
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    private fun parseDateOrNull(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        val trimmed = s.trim()
        for (fmt in dateFormats) {
            try { return LocalDate.parse(trimmed, fmt) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    private val timeFormats = listOf(
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    )

    private fun parseTimeOrNull(s: String?): LocalTime? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        for (f in timeFormats) {
            try { return LocalTime.parse(t, f) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    private var cachedRows: List<LookupRow>? = null
    private var cachedHeader: List<String>? = null
    /** Path or Uri.toString() of the currently loaded source */
    private var loadedFromPath: String? = null
    /** Last-modified timestamp (or any version token you track) */
    private var loadedFromLastModified: Long = -1L

    private fun effectiveTripType(row: LookupRow): String =
        row.tripType?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (!row.rtTime.isNullOrBlank()) "Return" else "Appt"

    private fun operativeTime(row: LookupRow): LocalTime? =
        when (effectiveTripType(row).lowercase()) {
            "appt" -> parseTimeOrNull(row.puTimeAppt) ?: parseTimeOrNull(row.rtTime)
            "return" -> parseTimeOrNull(row.rtTime) ?: parseTimeOrNull(row.puTimeAppt)
            else -> parseTimeOrNull(row.puTimeAppt) ?: parseTimeOrNull(row.rtTime)
        }

    fun tripsOnChrono(rows: List<LookupRow>, date: LocalDate): List<LookupRow> {
        val sameDay = rows.filter { r -> parseDateOrNull(r.driveDate) == date }
        return sameDay.sortedWith(
            compareBy<LookupRow> { operativeTime(it) == null } // nulls last
                .thenBy { operativeTime(it) }
                .thenBy { it.passenger.orEmpty().lowercase() }
                .thenBy { it.pAddress.orEmpty().lowercase() }
                .thenBy { it.dAddress.orEmpty().lowercase() }
        )
    }

    /** CSV DIAGNOSTIC — explain why rows fail with your current assumptions (kept from your version). */
    fun debugImportRejectionsFromFile(file: File, maxSamples: Int = 15) {
        if (!file.exists()) {
            Log.d(TAG, "DEBUG_IMPORT: file not found: ${file.absolutePath}")
            return
        }
        val br = file.bufferedReader(Charsets.UTF_8)
        var headerLine = br.readLine() ?: run {
            Log.d(TAG, "DEBUG_IMPORT: empty file"); return
        }
        if (headerLine.isNotEmpty() && headerLine[0].code == 0xFEFF) headerLine = headerLine.substring(1)
        val header = headerLine.split(',').map { it.trim() }
        Log.d(TAG, "DEBUG_IMPORT: header=$header (#=${header.size})")

        val missingExpected = EXPECTED_HEADER.filter { it !in header }
        if (missingExpected.isNotEmpty()) Log.d(TAG, "DEBUG_IMPORT: expected-but-missing headers: $missingExpected")

        var lineNo = 1
        var total = 0
        var accepted = 0
        var rejected = 0
        val reasons = mutableMapOf<String, Int>()
        val samples = mutableListOf<String>()
        fun reason(r: String) { reasons[r] = (reasons[r] ?: 0) + 1 }

        br.useLines { seq ->
            seq.forEach { line ->
                lineNo++; total++
                if (line.isBlank()) { reason("blank_line"); rejected++; if (samples.size < maxSamples) samples += "L$lineNo: blank_line"; return@forEach }
                val fields = line.split(',')
                val colCount = fields.size
                if (colCount != header.size) {
                    val hasQuotes = line.indexOf('"') >= 0
                    val code = if (hasQuotes && colCount > header.size) "colcount_over_header_quotes_present"
                    else if (colCount > header.size) "colcount_over_header"
                    else "colcount_under_header"
                    reason(code); rejected++
                    if (samples.size < maxSamples) samples += "L$lineNo: $code fields=$colCount header=${header.size} preview='${line.take(120)}'"
                    return@forEach
                }
                val row = header.indices.associate { h -> header[h] to fields[h].trim() }

                val missingReq = REQUIRED_COLS.filter { row[it].isNullOrEmpty() }
                if (missingReq.isNotEmpty()) {
                    reason("missing_required:${missingReq.joinToString("+")}"); rejected++
                    if (samples.size < maxSamples) samples += "L$lineNo: missing_required $missingReq | DriveDate='${row["DriveDate"]}' Passenger='${row["Passenger"]}'"
                    return@forEach
                }

                val rawDate = row["DriveDate"].orEmpty()
                val parsedCurrent = parseDateCurrent(rawDate)
                if (parsedCurrent == null) {
                    val parsedFlex = parseDateFlex(rawDate)
                    val code = if (parsedFlex != null) "date_fail_current_flex_ok" else "date_fail_both"
                    reason(code); rejected++
                    if (samples.size < maxSamples) samples += "L$lineNo: $code raw='$rawDate' flex=$parsedFlex"
                    return@forEach
                }

                val tripType = row["A/R"].orEmpty().ifEmpty { row["tripType"].orEmpty() }
                if (tripType.isEmpty()) {
                    reason("triptype_empty"); rejected++
                    if (samples.size < maxSamples) samples += "L$lineNo: triptype_empty Passenger='${row["Passenger"]}'"
                    return@forEach
                }
                accepted++
            }
        }
        Log.d(TAG, "DEBUG_IMPORT: total=$total accepted=$accepted rejected=$rejected")
        Log.d(TAG, "DEBUG_IMPORT: reasons=$reasons")
        samples.forEach { Log.d(TAG, "DEBUG_IMPORT: $it") }
    }

    /* ================== NEW: CSV IMPORT (canonicalizes header in-memory) ================== */

    // Quote-aware CSV line parser (handles commas inside quotes and "" escapes)
    private fun parseCsvLineQuoted(s: String): List<String> {
        val out = ArrayList<String>(16)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < s.length && s[i + 1] == '"') { sb.append('"'); i++ }
                    else { inQuotes = !inQuotes }
                }
                ch == ',' && !inQuotes -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    // Map variants → canonical names your importer expects
    private fun canonicalForHeader(h: String): String = when (h.trim()) {
        "tripType"   -> "A/R"
        "puTimeAppt" -> "PUTimeAppt"
        "rtTime"     -> "RTTime"
        else         -> h.trim()
    }

    /**
     * Reads a CSV and returns rows as LookupRow with header aliases rewritten in-memory.
     * Data lines are copied as-is. If there are extra fields beyond the header, merges them
     * into the last column (so addresses with commas are preserved).
     */
    fun importLookupCsv(csvFile: File): List<LookupRow> {
        if (!csvFile.exists()) {
            Log.d(TAG, "IMPORT: file not found ${csvFile.absolutePath}")
            return emptyList()
        }

        val lines = csvFile.readLines(utf8)
        if (lines.isEmpty()) return emptyList()

        // Header
        var headerLine = lines.first()
        if (headerLine.isNotEmpty() && headerLine[0].code == 0xFEFF) headerLine = headerLine.substring(1)
        val rawHeader = parseCsvLineQuoted(headerLine)
        val canonHeader = rawHeader.map(::canonicalForHeader)
        if (canonHeader != rawHeader) {
            Log.d(TAG, "CANON_HEADER_INMEM: $rawHeader -> $canonHeader")
        } else {
            Log.d(TAG, "CANON_HEADER_INMEM: no change")
        }

        val out = ArrayList<LookupRow>(lines.size.coerceAtLeast(8))
        var kept = 0
        var dropped = 0

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue

            val fields = parseCsvLineQuoted(line).map { it.trim() }
            val values =
                if (fields.size > canonHeader.size) {
                    // Merge extras into last column
                    val merged = ArrayList<String>(canonHeader.size)
                    merged.addAll(fields.take(canonHeader.size - 1))
                    merged.add(fields.drop(canonHeader.size - 1).joinToString(","))
                    merged
                } else if (fields.size < canonHeader.size) {
                    fields + List(canonHeader.size - fields.size) { "" }
                } else fields

            val rowMap = LinkedHashMap<String, String>(canonHeader.size)
            for (c in canonHeader.indices) rowMap[canonHeader[c]] = values[c]

            // Minimal accept rules (align with your DEBUG rules):
            if (rowMap["DriveDate"].isNullOrBlank() || rowMap["Passenger"].isNullOrBlank()) {
                dropped++; continue
            }
            if (parseDateCurrent(rowMap["DriveDate"] ?: "") == null) {
                dropped++; continue
            }
            val tripType = rowMap["A/R"].orEmpty().ifEmpty { rowMap["tripType"].orEmpty() }
            if (tripType.isEmpty()) {
                dropped++; continue
            }

            // Build LookupRow using canonical keys (fallbacks kept just in case)
            val lr = LookupRow(
                driveDate   = rowMap["DriveDate"],
                passenger   = rowMap["Passenger"],
                tripType    = rowMap["A/R"]?.ifEmpty { rowMap["tripType"] },
                pAddress    = rowMap["PAddress"],
                dAddress    = rowMap["DAddress"],
                puTimeAppt  = rowMap["PUTimeAppt"] ?: rowMap["puTimeAppt"],
                doTimeAppt  = rowMap["DOTimeAppt"],
                rtTime      = rowMap["RTTime"] ?: rowMap["rtTime"],
                phone       = rowMap["Phone"]
            )
            out += lr
            kept++
        }

        Log.d(TAG, "IMPORT_PARSE_DONE: header=$canonHeader rows=${out.size} (kept=$kept, dropped=$dropped)")
        return out
    }

    /* ================== end new import ================== */

    fun tripsOnChrono(context: Context, date: LocalDate): List<LookupRow> =
        tripsOnChrono(load(context), date)

    fun groupTripsByDate(rows: List<LookupRow>): SortedMap<LocalDate, List<LookupRow>> {
        val grouped = rows
            .mapNotNull { r -> parseDateOrNull(r.driveDate)?.let { it to r } }
            .groupBy({ it.first }, { it.second })
        return TreeMap(grouped)
    }

    fun groupTripsByDate(context: Context): SortedMap<LocalDate, List<LookupRow>> =
        groupTripsByDate(load(context))

    fun tripsOn(date: LocalDate, rows: List<LookupRow>): List<LookupRow> =
        groupTripsByDate(rows)[date] ?: emptyList()

    fun tripsOn(context: Context, date: LocalDate): List<LookupRow> =
        groupTripsByDate(context)[date] ?: emptyList()

    // LookupStore.kt
    fun invalidateLookupCache() {
        // clear whatever caches you maintain
        cachedRows = null
        cachedHeader = null
        loadedFromPath = null
        loadedFromLastModified = 0L
    }

    fun save(context: Context, rows: List<LookupRow>) {
        val f = file(context)
        val tmp = tmpFile(context)
        runCatching {
            tmp.writeText(gson.toJson(rows), utf8)
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText(utf8), utf8)
                tmp.delete()
            }
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }

    private fun norm(s: String?) = s?.trim()?.lowercase(Locale.US).orEmpty()
    private fun dateKey(s: String?): String {
        val d = parseDateOrNull(s)
        return d?.toString() ?: norm(s)
    }

    fun stableKey(r: LookupRow): String = listOf(
        dateKey(r.driveDate),
        norm(r.passenger),
        norm(r.pAddress),
        norm(r.dAddress)
    ).joinToString("|")

    fun mergeAndSave(context: Context, existing: List<LookupRow>, incoming: List<LookupRow>): List<LookupRow> {
        val map = LinkedHashMap<String, LookupRow>(existing.size + incoming.size)
        existing.forEach { map[stableKey(it)] = it }
        incoming.forEach { map[stableKey(it)] = it }
        return map.values.toList().also { save(context, it) }
    }

    fun upsert(context: Context, row: LookupRow): List<LookupRow> {
        val all = load(context).toMutableList()
        val k = stableKey(row)
        val idx = all.indexOfFirst { stableKey(it) == k }
        if (idx >= 0) all[idx] = row else all += row
        save(context, all)
        return all
    }

    fun remove(context: Context, row: LookupRow): Boolean {
        val all = load(context).toMutableList()
        val k = stableKey(row)
        val removed = all.removeAll { stableKey(it) == k }
        if (removed) save(context, all)
        return removed
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    // --- CSV Header cache for CustomerLookup.csv ---
    private var csvHeaders: List<String> = emptyList()

    fun loadCsvHeadersOnce(context: Context) {
        if (csvHeaders.isNotEmpty()) return
        try {
            val csvFile = File(
                android.os.Environment.getExternalStorageDirectory(),
                "PassengerSchedules/CustomerLookup.csv"
            )
            if (!csvFile.exists()) {
                Log.e("LookupCsvHeader", "CSV not found: ${csvFile.absolutePath}")
                return
            }

            val firstLine = csvFile.useLines { it.firstOrNull() } ?: return
            csvHeaders = firstLine.split(',').map { it.trim() }

            Log.d("LookupCsvHeader", "Loaded headers (${csvHeaders.size}): $csvHeaders")
        } catch (e: Exception) {
            Log.e("LookupCsvHeader", "Error reading CSV header", e)
        }
    }

    fun getCsvHeaders(): List<String> = csvHeaders
}
