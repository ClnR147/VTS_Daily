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
    val scheduleDateStr = scheduleDate.format(formatter)

    try {
        val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
        val fileName = "VTS $scheduleDateStr.xls"
        val file = File(folder, fileName)

        if (!file.exists()) {
            Log.d("DEBUG", "Schedule file not found: ${file.absolutePath}")
            return Schedule(scheduleDateStr, emptyList())
        }

        val workbook = Workbook.getWorkbook(file)
        val sheet = workbook.getSheet(0)

        val removedTrips = RemovedTripStore.getRemovedTrips(context, scheduleDate)
        Log.d("DEBUG", "Removed trips for $scheduleDateStr: ${removedTrips.size}")



        for (i in 0 until sheet.rows) {
            val row = sheet.getRow(i)
            if (row.size >= 6 && row[0].contents.isNotBlank()) {
                val passenger = Passenger(
                    name = row[0].contents.trim(),
                    id = row[1].contents.trim(),
                    pickupAddress = row[2].contents.trim(),
                    dropoffAddress = row[3].contents.trim(),
                    typeTime = row[4].contents.trim(),
                    phone = row[5].contents.trim()
                )

                val isRemoved = removedTrips.any {
                    it.name == passenger.name &&
                            it.pickupAddress == passenger.pickupAddress &&
                            it.dropoffAddress == passenger.dropoffAddress &&
                            it.typeTime == passenger.typeTime
                }

                if (!isRemoved) {
                    passengers.add(passenger)
                } else {
                    Log.d("DEBUG", "Skipping removed passenger: ${passenger.name} ${passenger.typeTime}")
                }
            }
        }

        workbook.close()
    } catch (e: IOException) {
        e.printStackTrace()
    } catch (e: BiffException) {
        e.printStackTrace()
    }
    Log.d("DEBUG", "Loaded ${passengers.size} passengers from $scheduleDateStr")
    return Schedule(scheduleDateStr, passengers)
}


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
