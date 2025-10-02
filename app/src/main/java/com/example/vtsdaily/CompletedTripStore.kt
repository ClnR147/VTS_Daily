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
    private val formatter = DateTimeFormatter.ofPattern("M-d-yy") // file/date format

    // ---------------------- File helpers ----------------------
    private fun getFile(context: Context, forDate: LocalDate): File {
        val dateFolder = forDate.format(formatter)
        val dir = File(context.filesDir, "completed-trips")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$dateFolder.json")
    }

    private fun load(context: Context, forDate: LocalDate): MutableList<CompletedTrip> {
        val file = getFile(context, forDate)
        if (!file.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<CompletedTrip>>() {}.type
        return gson.fromJson<MutableList<CompletedTrip>>(file.readText(), type) ?: mutableListOf()
    }

    private fun save(context: Context, forDate: LocalDate, list: List<CompletedTrip>) {
        val file = getFile(context, forDate)
        file.writeText(gson.toJson(list))
    }

    // ---------------------- Queries ----------------------
    fun getCompletedTrips(context: Context, forDate: LocalDate): List<CompletedTrip> {
        val trips = load(context, forDate)
        val timeFmt = DateTimeFormatter.ofPattern("H:mm")
        return trips.sortedBy {
            try {
                LocalTime.parse(it.typeTime.trim(), timeFmt)
            } catch (_: Exception) {
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

    // ---------------------- Mutations ----------------------
    fun addCompletedTrip(context: Context, forDate: LocalDate, passenger: Passenger) {
        val list = load(context, forDate)

        val exists = list.any {
            it.name == passenger.name &&
                    it.pickupAddress == passenger.pickupAddress &&
                    it.dropoffAddress == passenger.dropoffAddress &&
                    it.typeTime == passenger.typeTime
        }

        if (!exists) {
            list.add(
                CompletedTrip(
                    name = passenger.name,
                    pickupAddress = passenger.pickupAddress,
                    dropoffAddress = passenger.dropoffAddress,
                    typeTime = passenger.typeTime,
                    date = forDate.format(formatter),
                    completedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    phone = passenger.phone?.takeUnless { it.isBlank() }
                )
            )
            save(context, forDate, list)
        }
    }

    /**
     * Remove exactly one completed record. Returns true if something was removed.
     * Matches by name + time, and (optionally) pickup/dropoff addresses.
     */
    fun removeOne(
        context: Context,
        forDate: LocalDate,
        matchId: String? = null,          // ignored (no id in CompletedTrip)
        matchName: String,
        matchTime: String,
        pickupAddress: String? = null,
        dropoffAddress: String? = null
    ): Boolean {
        val list = load(context, forDate)

        val idx = list.indexOfFirst { ct ->
            val nameTimeMatches =
                ct.name.equals(matchName, ignoreCase = true) &&
                        ct.typeTime.trim() == matchTime.trim()

            val pickupOk = pickupAddress?.let { it == ct.pickupAddress } ?: true
            val dropoffOk = dropoffAddress?.let { it == ct.dropoffAddress } ?: true

            nameTimeMatches && pickupOk && dropoffOk
        }

        return if (idx >= 0) {
            list.removeAt(idx)
            save(context, forDate, list)
            true
        } else {
            false
        }
    }

    /**
     * Convenience wrapper: remove a completed record using a Passenger object.
     * Returns true if a record was removed.
     */
    fun removeCompletedTrip(context: Context, forDate: LocalDate, passenger: Passenger): Boolean {
        return removeOne(
            context = context,
            forDate = forDate,
            matchId = null, // no id in CompletedTrip; ignore
            matchName = passenger.name,
            matchTime = passenger.typeTime,
            pickupAddress = passenger.pickupAddress,
            dropoffAddress = passenger.dropoffAddress
        )
    }
}

