// TripNotesStore.kt
package com.example.vtsdaily.notes

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class TripNotesStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun notesFile(dateIso: String): File {
        val dir = File(context.filesDir, "trip_notes").apply { mkdirs() }
        return File(dir, "$dateIso.json")
    }

    fun load(dateIso: String): MutableMap<String, TripNote> {
        val f = notesFile(dateIso)
        if (!f.exists()) return mutableMapOf()
        return try {
            val list = json.decodeFromString<List<TripNote>>(f.readText())
            list
                .filter { it.hasMeaningfulContent() }          // 🔥 drop empty notes
                .associateBy { it.tripKey }
                .toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun save(dateIso: String, map: Map<String, TripNote>) {
        val f = notesFile(dateIso)

        val list = map.values
            .filter { it.hasMeaningfulContent() }              // 🔥 never persist empties
            .sortedBy { it.tripKey }

        if (list.isEmpty()) {
            if (f.exists()) f.delete()                          // optional but keeps it clean
            return
        }

        f.writeText(json.encodeToString(list))
    }

    /**
     * Save if meaningful; otherwise delete the record.
     * This is what makes the "notes exist" indicator disappear when both fields are blank.
     */
    fun upsertOrRemove(dateIso: String, note: TripNote) {
        if (note.tripKey.isBlank()) return

        val map = load(dateIso)

        if (!note.hasMeaningfulContent()) {
            map.remove(note.tripKey)
        } else {
            map[note.tripKey] = note.copy(lastUpdatedEpochMs = System.currentTimeMillis())
        }

        save(dateIso, map)
    }

    /**
     * Finds the most recent meaningful note for this passengerId.
     * (Meaningful = gateCode or noteText).
     */
    fun findMostRecentTextForPassenger(
        passengerId: String,
        excludeDateIso: String
    ): TripNote? {
        if (passengerId.isBlank()) return null

        val dir = File(context.filesDir, "trip_notes")
        if (!dir.exists()) return null

        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.nameWithoutExtension } // YYYY-MM-DD
            ?: return null

        for (file in files) {
            val dateIso = file.nameWithoutExtension
            if (dateIso == excludeDateIso) continue

            val notesForDay = load(dateIso).values
            val hit = notesForDay.firstOrNull {
                it.matchKey == passengerId && it.hasMeaningfulContent()
            }

            if (hit != null) return hit
        }

        return null
    }
}
