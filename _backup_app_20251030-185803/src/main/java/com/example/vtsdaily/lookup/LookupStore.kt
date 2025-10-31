package com.example.vtsdaily.lookup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LookupStore {
    private const val FILE_NAME = "PassengerLookup.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<LookupRow> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val type = object : TypeToken<List<LookupRow>>() {}.type
        return gson.fromJson<List<LookupRow>>(f.readText(), type) ?: emptyList()
    }

    fun save(context: Context, rows: List<LookupRow>) {
        file(context).writeText(gson.toJson(rows))
    }

    /** Merge existing + incoming, de-dupe by a stable key. */
    fun mergeAndSave(context: Context, existing: List<LookupRow>, incoming: List<LookupRow>): List<LookupRow> {
        fun key(r: LookupRow) =
            "${r.driveDate.trim()}|${r.passenger.trim().lowercase()}|${r.pAddress.trim()}|${r.dAddress.trim()}|${r.phone.trim()}"

        val map = LinkedHashMap<String, LookupRow>(existing.size + incoming.size)
        existing.forEach { map[key(it)] = it }
        incoming.forEach { map[key(it)] = it } // incoming overrides dupes if any fields changed

        val merged = map.values.toList()
        save(context, merged)
        return merged
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}

