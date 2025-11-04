package com.example.vtsdaily

import android.content.Context

import android.os.Environment
import android.util.Log
import jxl.Workbook
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Time helpers ----------

val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** Extracts the first time (HH:mm) from strings like "Appt 09:30-10:00" for sorting. */
fun toSortableTime(typeTime: String): LocalTime = try {
    val firstTime = typeTime.substringAfter(" ").substringBefore("-").trim()
    LocalTime.parse(firstTime, timeFormatter)
} catch (_: Exception) {
    LocalTime.MIDNIGHT
}

// ---------- External intents ----------

// ---------- Schedule loading ----------

/**
 * Resolve a .xls schedule file for the given date.
 * Tries a set of common naming patterns and then falls back to scanning the folder.
 * Note: JXL can only open .xls (not .xlsx/.xlsm), so we only return .xls files here.
 */
private fun resolveScheduleFile(date: LocalDate): File? {
    val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
    if (!folder.exists()) return null

    val mm = date.format(DateTimeFormatter.ofPattern("MM"))
    val m  = date.format(DateTimeFormatter.ofPattern("M"))
    val dd = date.format(DateTimeFormatter.ofPattern("dd"))
    val d  = date.format(DateTimeFormatter.ofPattern("d"))
    val yy = date.format(DateTimeFormatter.ofPattern("yy"))
    val y4 = date.format(DateTimeFormatter.ISO_LOCAL_DATE) // yyyy-MM-dd

    val candidates = listOf(
        "VTS $m-$d-$yy.xls",
        "VTS $mm-$dd-$yy.xls",
        "VTS_$y4.xls",
        "$y4.xls"
    )

    // 1) Exact-file checks first
    candidates.map { File(folder, it) }
        .firstOrNull { it.exists() }
        ?.let { return it }

    // 2) Fuzzy scan: case-insensitive, collapse multiple spaces
    val wanted = setOf(
        "vts $m-$d-$yy.xls",
        "vts $mm-$dd-$yy.xls",
        "vts_$y4.xls",
        "$y4.xls"
    )
    return folder.listFiles()
        ?.firstOrNull { f ->
            f.name.lowercase(Locale.US)
                .replace(Regex("\\s+"), " ") in wanted
        }
}

fun loadSchedule(context: Context, scheduleDate: LocalDate): Schedule {
    val passengers = mutableListOf<Passenger>()
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val scheduleDateStr = scheduleDate.format(formatter)

    var workbook: Workbook? = null

    try {
        // 1) Resolve file robustly (.xls only)
        val file = resolveScheduleFile(scheduleDate)
        if (file == null || !file.exists()) {
            Log.w("Schedule", "No .xls schedule file found for $scheduleDateStr")
            return Schedule(scheduleDateStr, emptyList())
        }

        // 2) Open workbook
        try {
            workbook = Workbook.getWorkbook(file) // jxl
        } catch (t: Throwable) {
            Log.e("Schedule", "Workbook open failed for ${file.name}", t)
            return Schedule(scheduleDateStr, emptyList())
        }

        // 3) Pick a sheet that actually has rows
        val sheet = try {
            (0 until workbook!!.numberOfSheets)
                .map { workbook.getSheet(it) }
                .firstOrNull { it != null && it.rows > 0 }
                ?: workbook.getSheet(0)
        } catch (t: Throwable) {
            Log.e("Schedule", "Sheet access failed", t)
            return Schedule(scheduleDateStr, emptyList())
        }

        // 4) Removed trips lookup
        val removedTrips = try {
            RemovedTripStore.getRemovedTrips(context, scheduleDate)
        } catch (t: Throwable) {
            Log.w("Schedule", "RemovedTripStore failed; proceeding without removed trips", t)
            emptyList()
        }

        // 5) Parse rows safely
        var skippedTooFewCols = 0
        var skippedBlankFirst = 0
        var skippedRemoved = 0

        for (i in 0 until sheet.rows) {
            val row = try { sheet.getRow(i) } catch (_: Throwable) { continue }

            if (row.size < 6) { skippedTooFewCols++; continue }

            val first = row[0].contents?.trim().orEmpty()
            if (first.isEmpty()) { skippedBlankFirst++; continue }

            val p = try {
                Passenger(
                    name = first,
                    id = row[1].contents.trim(),
                    pickupAddress = row[2].contents.trim(),
                    dropoffAddress = row[3].contents.trim(),
                    typeTime = row[4].contents.trim(),
                    phone = row[5].contents.trim()
                )
            } catch (_: Throwable) {
                continue
            }

            val isRemoved = removedTrips.any {
                it.name == p.name &&
                        it.pickupAddress == p.pickupAddress &&
                        it.dropoffAddress == p.dropoffAddress &&
                        it.typeTime == p.typeTime
            }

            if (isRemoved) { skippedRemoved++; continue }
            passengers.add(p)
        }

        if (skippedTooFewCols + skippedBlankFirst + skippedRemoved > 0) {
            Log.i(
                "Schedule",
                "Parsed ${passengers.size} rows; skipped cols=$skippedTooFewCols blank=$skippedBlankFirst removed=$skippedRemoved"
            )
        }

    } catch (_: Throwable) {
        return Schedule(scheduleDateStr, emptyList())
    } finally {
        try { workbook?.close() } catch (_: Throwable) {}
    }

    // 6) Sort using robust time extraction
    val sorted = passengers.sortedBy { toSortableTime(it.typeTime) }
    return Schedule(scheduleDateStr, sorted)
}
// ---------- Filename parsing helpers ----------
/** The common .xls names we accept for a given date (not exhaustive, just useful for tests/UI). */


// ---------- Date discovery (for picker/lists) ----------

/**
 * Returns available schedule dates by scanning PassengerSchedules for .xls files
 * whose names contain a recognizable date. We *exclude* .xlsm so the list only
 * shows files we can actually open with JXL.
 */
fun getAvailableScheduleDates(): List<LocalDate> {
    val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
    if (!folder.exists()) return emptyList()

    // Only .xls (JXL-readable), accept 0-padded month/day, various separators.
    val pattern = Regex(
        """^VTS\s+((?:0?[1-9]|1[0-2])[-_/\.](?:0?[1-9]|[12]\d|3[01])[-_/\.](?:\d{2}|\d{4}))\.xls$""",
        RegexOption.IGNORE_CASE
    )

    return folder.listFiles()
        ?.mapNotNull { file ->
            pattern.find(file.name)?.groupValues?.get(1)?.let { dateStr ->
                val parts = dateStr.split(Regex("[-_/\\.]"))
                if (parts.size != 3) return@let null
                val (mm, dd, yyraw) = parts
                val yy = yyraw.trim()
                val normalized = "${mm.toInt()}-${dd.toInt()}-$yy" // remove leading zeros

                val fmt = if (yy.length == 2) {
                    DateTimeFormatter.ofPattern("M-d-yy")
                } else {
                    DateTimeFormatter.ofPattern("M-d-uuuu")
                }

                runCatching { LocalDate.parse(normalized, fmt) }.getOrNull()
            }
        }
        ?.sortedDescending()
        ?: emptyList()
}
