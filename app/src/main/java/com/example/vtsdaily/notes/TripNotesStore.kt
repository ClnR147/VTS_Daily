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
            list.associateBy { it.tripKey }.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun save(dateIso: String, map: Map<String, TripNote>) {
        val f = notesFile(dateIso)
        val list = map.values.sortedBy { it.tripKey }
        f.writeText(json.encodeToString(list))
    }
}
