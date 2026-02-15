// TripNotesModels.kt
package com.example.vtsdaily.notes

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class TripNoteFlags(
    val callOnArrival: Boolean = false,
    val hasGateCode: Boolean = false,
    val needsRamp: Boolean = false,
    val blind: Boolean = false,
    val needsLift: Boolean = false,
    val usesCane: Boolean = false,
    val bringCarSeat: Boolean = false,
    val pets: Boolean = false,
    val person: Boolean = false,

    // pickup spot hints
    val pickupFront: Boolean = false,
    val pickupBack: Boolean = false,
    val pickupAlley: Boolean = false
)

@Serializable
data class TripNote(
    val tripKey: String,
    val matchKey: String = "",   // ← passenger.id
    val flags: TripNoteFlags = TripNoteFlags(),
    val gateCode: String = "",
    val noteText: String = "",
    val lastUpdatedEpochMs: Long = 0L
)

private fun TripNote.normalized(): TripNote =
    copy(
        gateCode = gateCode.trim(),
        noteText = noteText.trim()
    )

fun TripNoteFlags.hasAnyTrue(): Boolean =
    callOnArrival ||
            hasGateCode ||
            needsRamp ||
            blind ||
            needsLift ||
            usesCane ||
            bringCarSeat ||
            pets ||
            pickupFront ||
            pickupBack ||
            pickupAlley ||
            person

// Save-worthy (keeps flags-only notes)
fun TripNote.shouldPersist(): Boolean =
    gateCode.trim().isNotBlank() ||
            noteText.trim().isNotBlank() ||
            flags.hasAnyTrue()

// Notes-exist indicator (if you still want this to be gate/text only)
fun TripNote.hasNoteTextOrGate(): Boolean =
    gateCode.trim().isNotBlank() || noteText.trim().isNotBlank()


fun TripNote.hasMeaningfulContent(): Boolean =
    gateCode.trim().isNotBlank() || noteText.trim().isNotBlank()


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
