package com.example.vtsdaily.lookup

import android.content.Context
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// ------- Shared UI constants (used by DetailsPage etc.) -------

val CardGutter = 12.dp
val CardInner = 14.dp

// ------- Date helpers -------
private val dateFormats = listOf(
    "M/d/yyyy", "MM/dd/yyyy",
    "M/d/yy", "MM/dd/yy",     // 2-digit year support
    "yyyy-MM-dd",
    "MMMM d, yyyy"
).map { DateTimeFormatter.ofPattern(it, Locale.US) }

fun parseDateOrNull(s: String?): LocalDate? {
    val t = s?.trim().orEmpty()
    if (t.isEmpty()) return null
    for (fmt in dateFormats) {
        try { return LocalDate.parse(t, fmt) } catch (_: DateTimeParseException) {}
    }
    return null
}

fun formatDate(d: LocalDate): String = d.format(dateFormats.first())

fun groupTripsByDate(rows: List<LookupRow>): Map<LocalDate, List<LookupRow>> {
    return rows
        .mapNotNull { row -> parseDateOrNull(row.driveDate)?.let { it to row } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it })
}

// ------- Import / CSV helpers -------
fun logImportStart(file: File) {
    // Optional: add logging if you want to see the source on import start
    // println("DEBUG Import starting from: ${file.absolutePath}")
}

fun parseCsvLine(s: String): List<String> {
    val out = ArrayList<String>()
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

private val EXPECTED_HEADER = listOf(
    "DriveDate","Passenger","A/R","PAddress","DAddress","PUTimeAppt","DOTimeAppt","RTTime","Phone"
)
private val REQUIRED_COLS = setOf("DriveDate","Passenger")

fun debugImportRejectionsFromFile(file: File, maxSamples: Int = 15) {
    if (!file.exists()) return

    file.bufferedReader(Charset.forName("UTF-8")).use { br ->
        var headerLine = br.readLine() ?: return
        // strip BOM
        if (headerLine.isNotEmpty() && headerLine[0].code == 0xFEFF) headerLine = headerLine.substring(1)

        val header = parseCsvLine(headerLine).map { it.trim() }

        val missingExpected = EXPECTED_HEADER.filter { it !in header }
        if (missingExpected.isNotEmpty()) {
            println("DEBUG Import: missing expected header(s): ${missingExpected.joinToString()}")
            println("DEBUG Import: found headers: ${header.joinToString()}")
            return
        }

        var rejected = 0
        val reasons = mutableMapOf<String, Int>()
        val samples = mutableListOf<String>()
        fun reason(r: String) { reasons[r] = (reasons[r] ?: 0) + 1 }

        var lineNo = 1
        while (true) {
            val line = br.readLine() ?: break
            lineNo++

            if (line.isBlank()) {
                reason("blank_line"); rejected++
                if (samples.size < maxSamples) samples += "L$lineNo: blank_line"
                continue
            }

            val fields = parseCsvLine(line).map { it.trim() }
            val colCount = fields.size
            if (colCount != header.size) {
                val hasQuotes = '"' in line
                val code = when {
                    hasQuotes && colCount > header.size -> "colcount_over_header_quotes_present"
                    colCount > header.size -> "colcount_over_header"
                    else -> "colcount_under_header"
                }
                reason(code); rejected++
                if (samples.size < maxSamples) {
                    samples += "L$lineNo: $code fields=$colCount header=${header.size} preview='${line.take(160)}'"
                }
                continue
            }

            val row = header.indices.associate { h -> header[h] to fields[h] }

            val missingReq = REQUIRED_COLS.filter { row[it].isNullOrEmpty() }
            if (missingReq.isNotEmpty()) {
                reason("missing_required:${missingReq.joinToString("+")}")
                rejected++
                if (samples.size < maxSamples)
                    samples += "L$lineNo: missing_required $missingReq | DriveDate='${row["DriveDate"]}' Passenger='${row["Passenger"]}'"
                continue
            }

            val parsedDate = parseDateOrNull(row["DriveDate"])
            if (parsedDate == null) {
                reason("date_parse_fail"); rejected++
                if (samples.size < maxSamples) samples += "L$lineNo: date_parse_fail raw='${row["DriveDate"]}'"
                continue
            }

            val tripType = row["A/R"].orEmpty().ifEmpty { row["tripType"].orEmpty() }
            if (tripType.isEmpty()) {
                reason("triptype_empty"); rejected++
                if (samples.size < maxSamples) samples += "L$lineNo: triptype_empty Passenger='${row["Passenger"]}'"
                continue
            }
        }

        samples.forEach { println("DEBUG Import: $it") }
    }
}

// Canonicalize headers to expected names
private fun canonicalForHeader(h: String): String = when (h.trim()) {
    "tripType"   -> "A/R"
    "puTimeAppt" -> "PUTimeAppt"
    "rtTime"     -> "RTTime"
    else         -> h.trim()
}

fun canonicalizeCsvHeaderToTemp(src: File, context: Context): File {
    if (!src.exists()) return src

    val lines = src.readLines(Charsets.UTF_8)
    if (lines.isEmpty()) return src

    val rawHeader = lines.first()
    val header = parseCsvLine(
        if (rawHeader.isNotEmpty() && rawHeader[0].code == 0xFEFF) rawHeader.substring(1) else rawHeader
    )
    val rewritten = header.map(::canonicalForHeader)

    if (header == rewritten) return src

    // Write to temp in cache
    val temp = File(context.cacheDir, "CustomerLookup.canon.csv")
    FileOutputStream(temp, false).bufferedWriter(Charsets.UTF_8).use { w ->
        w.append(rewritten.joinToString(","))
        w.append('\n')
        for (i in 1 until lines.size) {
            w.append(lines[i])
            if (i < lines.lastIndex) w.append('\n')
        }
    }
    return temp
}

