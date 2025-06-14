package com.example.vtsdaily

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object InsertedTripStore {
    private val gson = Gson()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun getFile(context: Context, scheduleDate: LocalDate): File {
        val dir = File(context.filesDir, "inserted-trips")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${scheduleDate.format(formatter)}.json")
    }

    fun saveInsertedTrips(context: Context, scheduleDate: LocalDate, trips: List<Passenger>) {
        val file = getFile(context, scheduleDate)
        val json = gson.toJson(trips)
        file.writeText(json)
    }

    fun loadInsertedTrips(context: Context, scheduleDate: LocalDate): List<Passenger> {
        val file = getFile(context, scheduleDate)
        if (!file.exists()) return emptyList()

        val type = object : TypeToken<List<Passenger>>() {}.type
        return try {
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addInsertedTrip(context: Context, scheduleDate: LocalDate, trip: Passenger) {
        val existing = loadInsertedTrips(context, scheduleDate).toMutableList()
        existing.add(trip)
        saveInsertedTrips(context, scheduleDate, existing)
    }

    fun removeInsertedTrip(context: Context, scheduleDate: LocalDate, trip: Passenger) {
        val updated = loadInsertedTrips(context, scheduleDate).filterNot { it == trip }
        saveInsertedTrips(context, scheduleDate, updated)
    }
}


