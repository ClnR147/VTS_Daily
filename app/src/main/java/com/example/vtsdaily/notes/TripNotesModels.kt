// TripNotesModels.kt
package com.example.vtsdaily.notes

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class TripNoteFlags(
    val callOnArrival: Boolean = false,
    val hasGateCode: Boolean = false,
    val needsRamp: Boolean = false,
    val needsLift: Boolean = false,
    val usesCane: Boolean = false,
    val bringCarSeat: Boolean = false,

    // pickup spot hints
    val pickupFront: Boolean = false,
    val pickupBack: Boolean = false,
    val pickupAlley: Boolean = false
)

@Serializable
data class TripNote(
    val tripKey: String,
    val flags: TripNoteFlags = TripNoteFlags(),
    val gateCode: String = "",         // optional: store the actual code
    val noteText: String = "",
    val lastUpdatedEpochMs: Long = 0L
)

/**
 * Build a stable key for a trip.
 * Uses fields you already have in Passenger and the schedule date.
 */
fun buildTripKey(
    scheduleDateIso: String, // e.g. "2026-02-04"
    passengerId: String,
    name: String,
    pickupAddress: String,
    dropoffAddress: String,
    typeTime: String
): String {
    val raw = listOf(
        scheduleDateIso.trim(),
        passengerId.trim(),
        name.trim(),
        pickupAddress.trim(),
        dropoffAddress.trim(),
        typeTime.trim()
    ).joinToString("|")

    val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
