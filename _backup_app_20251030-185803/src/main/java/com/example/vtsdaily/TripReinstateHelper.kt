package com.example.vtsdaily.ui

import android.content.Context
import com.example.vtsdaily.Passenger
import com.example.vtsdaily.RemovedTripStore
import java.time.LocalDate

object TripReinstateHelper {
    fun reinstateTrip(context: Context, date: LocalDate, passenger: Passenger) {
        RemovedTripStore.removeRemovedTrip(context, date, passenger)
    }
}
