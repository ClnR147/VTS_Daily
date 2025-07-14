package com.example.vtsdaily

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalTime



object RemovedTripStore {
    private val gson = Gson()
    private val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    private fun getFile(context: Context, forDate: LocalDate): File {
        val dateFolder = forDate.format(formatter)
        val dir = File(context.filesDir, "removed-trips")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$dateFolder.json")
    }

    fun getRemovedTrips(context: Context, forDate: LocalDate): List<RemovedTrip> {
        val file = getFile(context, forDate)
        if (!file.exists()) return emptyList()

        val type = object : TypeToken<List<RemovedTrip>>() {}.type
        val trips: List<RemovedTrip> = gson.fromJson(file.readText(), type)

        val formatter = DateTimeFormatter.ofPattern("H:mm")
        return trips.sortedBy {
            try {
                LocalTime.parse(it.typeTime.trim(), formatter)
            } catch (e: Exception) {
                LocalTime.MIDNIGHT
            }
        }
    }


    fun isTripRemoved(context: Context, forDate: LocalDate, passenger: Passenger): Boolean {
        return getRemovedTrips(context, forDate).any {
            it.name == passenger.name &&
                    it.pickupAddress == passenger.pickupAddress &&
                    it.dropoffAddress == passenger.dropoffAddress &&
                    it.typeTime == passenger.typeTime
        }
    }

    fun addRemovedTrip(context: Context, forDate: LocalDate, passenger: Passenger, reason: TripRemovalReason) {
        val file = getFile(context, forDate)
        val type = object : TypeToken<MutableList<RemovedTrip>>() {}.type
        val list: MutableList<RemovedTrip> =
            if (file.exists()) gson.fromJson(file.readText(), type)
            else mutableListOf()

        list.add(
            RemovedTrip(
                name = passenger.name,
                pickupAddress = passenger.pickupAddress,
                dropoffAddress = passenger.dropoffAddress,
                typeTime = passenger.typeTime,
                date = forDate.format(formatter),
                reason = reason
            )
        )

        file.writeText(gson.toJson(list))
    }
    fun removeRemovedTrip(context: Context, forDate: LocalDate, passenger: Passenger) {
        val file = getFile(context, forDate)
        if (!file.exists()) return

        val type = object : TypeToken<MutableList<RemovedTrip>>() {}.type
        val list: MutableList<RemovedTrip> = gson.fromJson(file.readText(), type)

        val updatedList = list.filterNot {
            it.name == passenger.name &&
                    it.pickupAddress == passenger.pickupAddress &&
                    it.dropoffAddress == passenger.dropoffAddress &&
                    it.typeTime == passenger.typeTime
        }

        file.writeText(gson.toJson(updatedList))
    }

}
