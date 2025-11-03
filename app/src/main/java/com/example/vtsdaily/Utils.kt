package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import jxl.Workbook
import jxl.read.biff.BiffException
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.util.Log



val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

fun toSortableTime(typeTime: String): LocalTime {
    return try {
        val firstTime = typeTime.substringAfter(" ").substringBefore("-").trim()
        LocalTime.parse(firstTime, timeFormatter)
    } catch (e: Exception) {
        LocalTime.MIDNIGHT
    }
}

fun launchWaze(context: Context, address: String) {
    val encoded = Uri.encode(address)
    val uri = Uri.parse("https://waze.com/ul?q=$encoded&navigate=yes")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.waze")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Waze not installed", Toast.LENGTH_SHORT).show()
    }
}

fun loadSchedule(context: Context, scheduleDate: LocalDate): Schedule {

    val passengers = mutableListOf<Passenger>()
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val timeFormatter = DateTimeFormatter.ofPattern("H:mm")
    val scheduleDateStr = scheduleDate.format(formatter)

    var workbook: Workbook? = null

    try {
        // 1) Resolve file
        val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
        val fileName = "VTS $scheduleDateStr.xls"
        val file = File(folder, fileName)

        if (!file.exists()) {
            return Schedule(scheduleDateStr, emptyList())
        }

        // 2) Open workbook (guard every step)
        try {
            workbook = Workbook.getWorkbook(file) // jxl
        } catch (t: Throwable) {
            return Schedule(scheduleDateStr, emptyList())
        }

        // 3) Choose a sheet that actually has rows
        val sheet = try {
            val s = (0 until workbook!!.numberOfSheets)
                .map { workbook!!.getSheet(it) }
                .firstOrNull { it != null && it.rows > 0 }
                ?: workbook!!.getSheet(0)
            s
        } catch (t: Throwable) {
            return Schedule(scheduleDateStr, emptyList())
        }

        // 4) Quick peek at first few rows
        try {
            for (i in 0 until minOf(sheet.rows, 5)) {
                val row = sheet.getRow(i)
                val preview = row.joinToString(" | ") { it.contents.trim() }
            }
        } catch (t: Throwable) {
        }

        // 5) Removed trips lookup
        val removedTrips = try {
            RemovedTripStore.getRemovedTrips(context, scheduleDate)
        } catch (t: Throwable) {
            emptyList()
        }

        // 6) Parse rows safely
        var skippedTooFewCols = 0
        var skippedBlankFirst = 0
        var skippedRemoved = 0

        for (i in 0 until sheet.rows) {
            val row = try { sheet.getRow(i) } catch (t: Throwable) {
                 continue
            }

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
            } catch (t: Throwable) {
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


    } catch (t: Throwable) {

        return Schedule(scheduleDateStr, emptyList())
    } finally {
        try {
            workbook?.close()

        } catch (t: Throwable) {

        }
    }

    // 7) Sort (safe)
    val sorted = passengers.sortedBy {
        try { LocalTime.parse(it.typeTime.trim(), timeFormatter) }
        catch (_: Throwable) { LocalTime.MIDNIGHT }
    }

    return Schedule(scheduleDateStr, sorted)
}



// Utils.kt
private val SCHEDULE_FILE_RE = Regex(
    pattern = """^(?:VTS[ _-]?)?(?:
        (?<y4>\d{4})[ _-](?<m>\d{1,2})[ _-](?<d>\d{1,2}) |   # YYYY-MM-DD
        (?<m2>\d{1,2})[ _-](?<d2>\d{1,2})[ _-](?<y2>\d{2})    # MM-DD-YY
    )\.xls$""".trimIndent().replace("\n",""),
    option = RegexOption.IGNORE_CASE
)

fun parseScheduleDateFromName(name: String): LocalDate? {
    val m = SCHEDULE_FILE_RE.matchEntire(name) ?: return null
    return when {
        m.groups["y4"] != null -> {
            LocalDate.of(
                m.groups["y4"]!!.value.toInt(),
                m.groups["m"]!!.value.toInt(),
                m.groups["d"]!!.value.toInt()
            )
        }
        m.groups["y2"] != null -> {
            val y = 2000 + m.groups["y2"]!!.value.toInt() // treat 2-digit year as 20xx
            LocalDate.of(
                y,
                m.groups["m2"]!!.value.toInt(),
                m.groups["d2"]!!.value.toInt()
            )
        }
        else -> null
    }
}

/** Optional: generate the common file names you accept for a given date */
fun candidateScheduleNamesFor(date: LocalDate): List<String> = listOf(
    "VTS ${date.format(DateTimeFormatter.ofPattern("MM-dd-yy"))}.xls",
    "VTS_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}.xls",
    date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".xls"
)


fun getAvailableScheduleDates(): List<LocalDate> {
    val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
    if (!folder.exists()) return emptyList()

    val pattern = Regex("""VTS (\d{1,2}-\d{1,2}-\d{2})\.xls""")
    return folder.listFiles()
        ?.mapNotNull { file ->
            pattern.find(file.name)?.groupValues?.get(1)?.let {
                runCatching {
                    LocalDate.parse(it, DateTimeFormatter.ofPattern("M-d-yy"))
                }.getOrNull()
            }
        }
        ?.sortedDescending()
        ?: emptyList()
}
