package com.example.vtsdaily.prediction

import com.example.vtsdaily.sanitizeName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Trip type as we care about it for prediction.
 * Keep it simple: only Appt (A) and Return (R).
 */
enum class TripType {
    APPT,
    RETURN
}

/**
 * A single historical trip instance, tied to a specific date.
 * This is the "normalized" data the engine works with.
 */
data class HistoryTrip(
    val date: LocalDate,
    val passengerName: String,
    val isDeceased: Boolean,      // 👈 add this back
    val tripType: TripType,
    val puTimeAppt: LocalTime?,   // Appointment pickup time (A)
    val rtTime: LocalTime?,       // Return time (R)
    val pAddress: String,
    val dAddress: String
)
/**
 * A predicted trip for the target date.
 * Same shape as what your UI need, plus appearancePercent
 * so you can show / debug the prediction strength if you want.
 */
data class PredictedTrip(
    val date: LocalDate,
    val passengerName: String,
    val tripType: TripType,
    val puTimeAppt: LocalTime?,
    val rtTime: LocalTime?,
    val pAddress: String,
    val dAddress: String,
    val appearancePercent: Double
)

/**
 * Internal aggregation while we scan the history window.
 */
private data class PassengerAggregate(
    val passengerKey: String,
    var displayName: String,
    val appearanceDates: MutableSet<LocalDate> = mutableSetOf(),
    var mostRecentAppt: HistoryTrip? = null,
    var mostRecentReturn: HistoryTrip? = null
)

object PredictionEngine {

    /**
     * Core prediction function.
     *
     * @param targetDate The date we want to predict a schedule for.
     * @param historySameWeekdayCount How many *same-weekday occurrences* to look back (default 30).
     *                               e.g. 30 Mondays for a Monday target.
     * @param minAppearanceFraction   Minimum fraction (0.30 = 30%) of appearances needed to include passenger.
     * @param loadHistoryTripsForDate Given a date, returns the list of HistoryTrip for that date.
     *                                You will typically wrap your existing loadTripsForDate() + mapping.
     */
    fun predictScheduleForDate(
        targetDate: LocalDate,
        historySameWeekdayCount: Int = 30,
        minAppearanceFraction: Double = 0.30,
        loadHistoryTripsForDate: (LocalDate) -> List<HistoryTrip>
    ): List<PredictedTrip> {
        require(historySameWeekdayCount > 0) { "historySameWeekdayCount must be > 0" }
        require(minAppearanceFraction in 0.0..1.0) { "minAppearanceFraction must be between 0.0 and 1.0" }

        val targetDow: DayOfWeek = targetDate.dayOfWeek

        // Step 1: find the last N same-weekday dates BEFORE the target date
        val historyDates = mutableListOf<LocalDate>()
        var cursor = targetDate.minusDays(1)

        while (historyDates.size < historySameWeekdayCount) {
            if (cursor.dayOfWeek == targetDow) {
                historyDates.add(cursor)
            }
            cursor = cursor.minusDays(1)
        }

        if (historyDates.isEmpty()) {
            return emptyList()
        }

        val passengerMap = mutableMapOf<String, PassengerAggregate>()

        // Step 2: load trips for each history date and aggregate per passenger
        historyDates.forEach { historyDate ->
            val tripsForDay = loadHistoryTripsForDate(historyDate)

            for (trip in tripsForDay) {
                val key = passengerKey(trip.passengerName)
                val existing = passengerMap[key]
                val aggregate = if (existing == null) {
                    PassengerAggregate(
                        passengerKey = key,
                        displayName = trip.passengerName
                    )
                } else {
                    existing
                }

                // update base properties
                aggregate.displayName = trip.passengerName // keep freshest spelling
                aggregate.appearanceDates.add(historyDate)

                // track most recent Appt / Return by trip.date
                when (trip.tripType) {
                    TripType.APPT -> {
                        val current = aggregate.mostRecentAppt
                        if (current == null || trip.date.isAfter(current.date)) {
                            aggregate.mostRecentAppt = trip
                        }
                    }

                    TripType.RETURN -> {
                        val current = aggregate.mostRecentReturn
                        if (current == null || trip.date.isAfter(current.date)) {
                            aggregate.mostRecentReturn = trip
                        }
                    }
                }

                passengerMap[key] = aggregate
            }
        }

        val totalHistoryDays = historyDates.size.toDouble()

        // Step 3: build predicted trips
        val predicted = mutableListOf<PredictedTrip>()

        for ((_, agg) in passengerMap) {
            val appearanceCount = agg.appearanceDates.size
            val appearanceFraction = if (totalHistoryDays > 0) {
                appearanceCount / totalHistoryDays
            } else {
                0.0
            }

            if (appearanceFraction < minAppearanceFraction) {
                continue
            }

            // If they ever had an Appt in the window, add a predicted Appt row
            agg.mostRecentAppt?.let { lastAppt ->
                predicted.add(
                    PredictedTrip(
                        date = targetDate,
                        passengerName = agg.displayName,
                        tripType = TripType.APPT,
                        puTimeAppt = lastAppt.puTimeAppt,
                        rtTime = null,
                        pAddress = lastAppt.pAddress,
                        dAddress = lastAppt.dAddress,
                        appearancePercent = appearanceFraction
                    )
                )
            }

            // If they ever had a Return in the window, add a predicted Return row
            agg.mostRecentReturn?.let { lastReturn ->
                predicted.add(
                    PredictedTrip(
                        date = targetDate,
                        passengerName = agg.displayName,
                        tripType = TripType.RETURN,
                        puTimeAppt = null,
                        rtTime = lastReturn.rtTime,
                        pAddress = lastReturn.pAddress,
                        dAddress = lastReturn.dAddress,
                        appearancePercent = appearanceFraction
                    )
                )
            }
        }

        // Step 4: sort predicted rows by:
        // RTTime (if Return) else PUTimeAppt; nulls go to the bottom.
        return predicted.sortedWith { a, b ->
            val aTime = a.rtTime ?: a.puTimeAppt ?: LocalTime.MAX
            val bTime = b.rtTime ?: b.puTimeAppt ?: LocalTime.MAX
            val cmp = aTime.compareTo(bTime)
            if (cmp != 0) cmp else a.passengerName.compareTo(b.passengerName)
        }
    }

    /**
     * Normalize a passenger name into a stable key.
     * Reuses your sanitizeName() helper.
     */
    private fun passengerKey(name: String): String {
        return sanitizeName(name).trim().lowercase()
    }
}

