package com.example.vtsdaily

data class Passenger(
    val name: String,
    val id: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val typeTime: String,
    val phone: String
)

data class CompletedTrip(
    val name: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val typeTime: String,
    val date: String,
    val completedAt: String? = null
)

data class Schedule(
    val date: String,
    val passengers: List<Passenger>
)

enum class TripRemovalReason {
    CANCELLED, NO_SHOW, REMOVED, COMPLETED
}

data class RemovedTrip(
    val name: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val typeTime: String,
    val date: String,
    val reason: TripRemovalReason = TripRemovalReason.CANCELLED
)

enum class TripViewMode {
    ACTIVE, COMPLETED, REMOVED
}


