package com.example.vtsdaily

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object CompletedTripStore {
    private val gson = Gson()
    private val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    private fun getFile(context: Context, forDate: LocalDate): File {
        val dateFolder = forDate.format(formatter)
        val dir = File(context.filesDir, "completed-trips")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$dateFolder.json")
    }

    fun getCompletedTrips(context: Context, forDate: LocalDate): List<CompletedTrip> {
        val file = getFile(context, forDate)
        if (!file.exists()) return emptyList()

        val type = object : TypeToken<List<CompletedTrip>>() {}.type
        val trips: List<CompletedTrip> = gson.fromJson(file.readText(), type)

        val formatter = DateTimeFormatter.ofPattern("H:mm")
        return trips.sortedBy {
            try {
                LocalTime.parse(it.typeTime.trim(), formatter)
            } catch (e: Exception) {
                LocalTime.MIDNIGHT
            }
        }
    }


    fun isTripCompleted(context: Context, forDate: LocalDate, passenger: Passenger): Boolean {
        return getCompletedTrips(context, forDate).any {
            it.name == passenger.name &&
                    it.pickupAddress == passenger.pickupAddress &&
                    it.dropoffAddress == passenger.dropoffAddress &&
                    it.typeTime == passenger.typeTime
        }
    }

    fun addCompletedTrip(context: Context, forDate: LocalDate, passenger: Passenger) {
        val file = getFile(context, forDate)
        val type = object : TypeToken<MutableList<CompletedTrip>>() {}.type
        val list: MutableList<CompletedTrip> =
            if (file.exists()) gson.fromJson(file.readText(), type)
            else mutableListOf()

        if (list.none {
                it.name == passenger.name &&
                        it.pickupAddress == passenger.pickupAddress &&
                        it.dropoffAddress == passenger.dropoffAddress &&
                        it.typeTime == passenger.typeTime
            }) {
            list.add(
                CompletedTrip(
                    name = passenger.name,
                    pickupAddress = passenger.pickupAddress,
                    dropoffAddress = passenger.dropoffAddress,
                    typeTime = passenger.typeTime,
                    date = forDate.format(formatter),
                    completedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                )
            )
            file.writeText(gson.toJson(list))
        }
    }
}
