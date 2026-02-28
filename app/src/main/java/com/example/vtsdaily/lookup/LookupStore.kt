package com.example.vtsdaily.lookup

import android.content.Context
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

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
    private fun tmpFile(context: Context) = File(context.filesDir, "$FILE_NAME$TMP_SUFFIX")

    fun load(context: Context): List<LookupRow> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val json = f.readText(utf8)
            val type = object : TypeToken<List<LookupRow>>() {}.type
            val rows = gson.fromJson<List<LookupRow>>(json, type) ?: emptyList()
            rows.distinctBy { stableKey(it) }   // ✅ collapses existing duplicates too
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

    /* ================== end new import ================== */

    fun groupTripsByDate(rows: List<LookupRow>): SortedMap<LocalDate, List<LookupRow>> {
        val grouped = rows
            .mapNotNull { r -> parseDateOrNull(r.driveDate)?.let { it to r } }
            .groupBy({ it.first }, { it.second })
        return TreeMap(grouped)
    }

    fun groupTripsByDate(context: Context): SortedMap<LocalDate, List<LookupRow>> =
        groupTripsByDate(load(context))

    // LookupStore.kt
    fun invalidateLookupCache() {
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

    private fun normAddr(s: String?): String {
        val x = s?.trim()?.lowercase(Locale.US).orEmpty()
        if (x.isEmpty()) return ""
        return x
            .replace(".", " ")                 // dots -> spaces (handles Rd.1203a)
            .replace(",", " ")                 // commas -> spaces
            .replace(Regex("\\s+"), " ")       // collapse whitespace
            .trim()
    }

    fun stableKey(r: LookupRow): String = listOf(
        dateKey(r.driveDate),
        norm(r.passenger),
        normAddr(r.pAddress),
        normAddr(r.dAddress)
    ).joinToString("|")

    fun mergeAndSave(context: Context, existing: List<LookupRow>, incoming: List<LookupRow>): List<LookupRow> {
        val map = LinkedHashMap<String, LookupRow>(existing.size + incoming.size)
        existing.forEach { map[stableKey(it)] = it }
        incoming.forEach { map[stableKey(it)] = it }
        return map.values.toList().also { save(context, it) }
    }

    // --- CSV Header cache for CustomerLookup.csv ---
    private var csvHeaders: List<String> = emptyList()

}
