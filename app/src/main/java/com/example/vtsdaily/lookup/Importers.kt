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
 * Automatically supports optional fields (TripType / PU / DO / RT) if present.
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

            // ---------- tolerant header lookup with aliases ----------
            fun findIdx(vararg aliases: String): Int =
                indexOfHeader(clean, aliases.toList())

            data class Cols(
                val driveDate: Int,
                val passenger: Int,
                val ar: Int,
                val pAddr: Int,
                val dAddr: Int,
                val puAppt: Int,
                val doAppt: Int,
                val rt: Int,
                val phone: Int
            )

            val cols = Cols(
                driveDate = findIdx("DriveDate", "Date", "Drive Date"),
                passenger = findIdx("Passenger", "Name"),
                ar        = findIdx("A/R", "AR", "TripType", "Type"),
                pAddr     = findIdx("PAddress", "PickupAddress", "P Address"),
                dAddr     = findIdx("DAddress", "DropAddress", "D Address"),
                puAppt    = findIdx("PUTimeAppt", "puTimeAppt", "Appt", "ApptTime", "AppointmentTime"),
                doAppt    = findIdx("DOTimeAppt", "doTimeAppt", "DropOffAppt", "Drop Appt"),
                rt        = findIdx("RTTime", "ReturnTime", "RT"),
                phone     = findIdx("Phone", "PhoneNumber")
            )

            // Require all 9 (using aliases where needed)
            val missingAny = listOf(
                "DriveDate" to cols.driveDate,
                "Passenger" to cols.passenger,
                "A/R"       to cols.ar,
                "PAddress"  to cols.pAddr,
                "DAddress"  to cols.dAddr,
                "PUTimeAppt/puTimeAppt" to cols.puAppt,
                "DOTimeAppt/doTimeAppt" to cols.doAppt,
                "RTTime"    to cols.rt,
                "Phone"     to cols.phone
            ).filter { it.second < 0 }

            if (missingAny.isNotEmpty()) {
                Log.e(TAG_IMPORT, "Missing required/alias columns: $missingAny  | Cleaned header=$clean")
                return emptyList()
            }

            dumpHeaderForLogcat(header)
            // --------------------------------------------------------

            fun at(row: Array<String>, i: Int): String? =
                row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }

            val out = mutableListOf<LookupRow>()
            reader.forEach { row ->
                val raw = mutableMapOf<String, String?>()

                // Populate raw with the cleaned header names for debugging
                clean.forEachIndexed { i, name -> raw[name] = at(row, i) }

                // Canonical keys so downstream code can rely on exact names
                raw["PUTimeAppt"] = at(row, cols.puAppt)
                raw["DOTimeAppt"] = at(row, cols.doAppt)   // <- canonical Drop-off time
                raw["RTTime"]     = at(row, cols.rt)

                val driveDate = at(row, cols.driveDate)
                val passenger = at(row, cols.passenger) ?: return@forEach
                val pAddr     = at(row, cols.pAddr) ?: ""
                val dAddr     = at(row, cols.dAddr) ?: ""
                val phone     = at(row, cols.phone)

                val ar = at(row, cols.ar)?.trim()
                val tripType = when (ar?.firstOrNull()?.uppercaseChar()) {
                    'A' -> "appt"
                    'R' -> "return"
                    else -> null
                }

                val pu = raw["PUTimeAppt"]
                val doAppt = raw["DOTimeAppt"]
                val rt = raw["RTTime"]

                out += try {
                    LookupRow(
                        driveDate = driveDate,
                        passenger = passenger,
                        pAddress = pAddr,
                        dAddress = dAddr,
                        phone = phone,
                        tripType = tripType,
                        puTimeAppt = pu,
                        doTimeAppt = doAppt,   // <-- now populated
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
