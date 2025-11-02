package com.example.vtsdaily.lookup

import android.util.Log
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale
import java.io.BufferedInputStream


private const val TAG_IMPORT = "LookupCsv"

// Normalize header names for tolerant, case-insensitive matching
private fun String.norm(): String =
    lowercase(Locale.US)
        .trim()
        .replace("\\s+".toRegex(), " ")
        .replace(".", "")
        .replace("_", " ")
        .replace("-", " ")
        .trim()

private fun indexOfHeader(headers: List<String>, aliases: List<String>): Int {
    val normH = headers.map { it.norm() }
    val want = aliases.map { it.norm() }.toSet()
    return normH.indexOfFirst { it in want }
}

/** Logs ALL header cells exactly as they appear in the CSV (no guessing). */
private fun dumpHeaderForLogcat(headers: List<String>) {
    Log.d(TAG_IMPORT, "CSV HEADER COUNT = ${headers.size}")
    headers.forEachIndexed { idx, name ->
        val visible = name.replace(" ", "␠")
        val last = name.lastOrNull()
        val lastCode = last?.code?.let { String.format("U+%04X", it) } ?: "—"
        val codePoints = name.codePoints().toArray().joinToString(", ") { "U+%04X".format(it) }
        Log.d(TAG_IMPORT, "hdr[$idx] raw='${name}' | visible='${visible}' | len=${name.length} | last=${last ?: '∅'} ($lastCode)")
        Log.d(TAG_IMPORT, "hdr[$idx] codepoints = [$codePoints]")
    }
}

/**
 * Works with your current 5-column file:
 *   DriveDate, Passenger, PAddress, DAddress, Phone
 *
 * Automatically supports optional fields (TripType / PU / RT) if present.
 * Call sites: import com.example.vtsdaily.lookup.importLookupCsv
 */
fun detectCsvSeparator(file: File, charset: Charset = Charsets.UTF_8): Char {
    if (!file.exists()) return ','
    BufferedInputStream(FileInputStream(file)).use { bis ->
        val buf = ByteArray(4096)
        val n = bis.read(buf)
        if (n <= 0) return ','
        val s = buf.copyOf(n).toString(charset)
        val first = s.substringBefore('\n').substringBefore('\r')

        // Count delimiters ignoring quoted commas/semicolons/tabs
        var inQuotes = false
        var cCommas = 0; var cSemis = 0; var cTabs = 0
        var i = 0
        while (i < first.length) {
            val ch = first[i]
            when (ch) {
                '"' -> {
                    // handle escaped quotes "" inside quotes
                    if (inQuotes && i + 1 < first.length && first[i + 1] == '"') { i++ }
                    else inQuotes = !inQuotes
                }
                ',' -> if (!inQuotes) cCommas++
                ';' -> if (!inQuotes) cSemis++
                '\t'-> if (!inQuotes) cTabs++
            }
            i++
        }
        return when {
            cTabs  >= cCommas && cTabs  >= cSemis -> '\t'
            cSemis >= cCommas && cSemis >= cTabs  -> ';'
            else -> ','
        }
    }
}

/** Read the ENTIRE header row with OpenCSV using the given (or detected) separator. */
fun readCsvHeaderAll(
    file: File,
    charset: Charset = Charsets.UTF_8,
    separator: Char? = null
): List<String> {
    if (!file.exists()) return emptyList()
    val sep = separator ?: detectCsvSeparator(file, charset)

    val parser = CSVParserBuilder()
        .withSeparator(sep)
        .withIgnoreQuotations(false)
        .build()

    CSVReaderBuilder(InputStreamReader(FileInputStream(file), charset))
        .withCSVParser(parser)
        .build().use { reader ->
            val header = reader.readNext() ?: return emptyList()
            return header.toList()
        }
}

fun importLookupCsv(
    file: File,
    charset: Charset = Charsets.UTF_8
): List<LookupRow> {
    if (!file.exists()) return emptyList()

    val sep = detectCsvSeparator(file, charset)
    val parser = CSVParserBuilder()
        .withSeparator(sep)
        .withIgnoreQuotations(false)
        .build()

    CSVReaderBuilder(InputStreamReader(FileInputStream(file), charset))
        .withCSVParser(parser)
        .build().use { reader ->

            val header = reader.readNext()?.toList() ?: return emptyList()
            // Normalize header cells
            val clean = header.mapIndexed { i, h ->
                val s = h.trim()
                if (i == 0) s.removePrefix("\uFEFF") else s
            }

// Require ALL 9 exact names against the cleaned header
            val required = listOf(
                "DriveDate","Passenger","A/R","PAddress","DAddress",
                "PUTimeAppt","DOTimeAppt","RTTime","Phone"
            )
            val indexByName = required.associateWith { name -> clean.indexOf(name) }
            val missing = indexByName.filterValues { it < 0 }.keys
            if (missing.isNotEmpty()) {
                Log.e(TAG_IMPORT, "Missing required columns: $missing  | Cleaned header=$clean")
                return emptyList()
            }

            dumpHeaderForLogcat(header)

            // Require ALL 9 exact columns

            val idx = required.associateWith { h -> header.indexOf(h) }
            if (idx.values.any { it < 0 }) {
                Log.e(TAG_IMPORT, "Missing required columns. Raw headers=$header")
                return emptyList()
            }

            fun cell(row: Array<String>, i: Int): String? =
                row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }

            val out = mutableListOf<LookupRow>()
            reader.forEach { row ->
                val raw = mutableMapOf<String, String?>()
                clean.forEachIndexed { i, name ->
                    raw[name] = row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
                }

                val driveDate = raw["DriveDate"]
                val passenger = raw["Passenger"] ?: return@forEach
                val pAddr     = raw["PAddress"] ?: ""
                val dAddr     = raw["DAddress"] ?: ""
                val phone     = raw["Phone"]

                val ar = raw["A/R"]?.trim()
                val pu = raw["PUTimeAppt"]
                val rt = raw["RTTime"]
                val tripType = when (ar?.firstOrNull()?.uppercaseChar()) {
                    'A' -> "appt"
                    'R' -> "return"
                    else -> null
                }

                out += try {
                    LookupRow(
                        driveDate = driveDate,
                        passenger = passenger,
                        pAddress = pAddr,
                        dAddress = dAddr,
                        phone = phone,
                        tripType = tripType,
                        puTimeAppt = pu,
                        rtTime = rt,
                        raw = raw
                    )
                } catch (_: Throwable) {
                    LookupRow(driveDate, passenger, pAddr, dAddr, phone)
                }
            }


            Log.d(TAG_IMPORT, "importLookupCsv parsed ${out.size} rows from ${file.name}")
            return out
        }
}