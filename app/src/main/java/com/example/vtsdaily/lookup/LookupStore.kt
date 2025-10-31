package com.example.vtsdaily.lookup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.SortedMap
import java.util.TreeMap
import java.util.Locale


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

    // --- Phase 1: group trips by date (LookupRow.driveDate is a String) ---

    // Try a few common formats so old exports still work.
    private val dateFormats = listOf(
        "M/d/yyyy",          // 1/7/2025
        "MM/dd/yyyy",        // 01/07/2025
        "yyyy-MM-dd",        // 2025-01-07
        "MMMM d, yyyy"       // January 7, 2025
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    private fun parseDateOrNull(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        for (fmt in dateFormats) {
            try { return LocalDate.parse(s.trim(), fmt) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    /** Group rows by LocalDate (sorted). Invalid/missing dates are skipped. */
    fun groupTripsByDate(rows: List<LookupRow>): SortedMap<LocalDate, List<LookupRow>> {
        val grouped = rows
            .mapNotNull { r -> parseDateOrNull(r.driveDate)?.let { it to r } }
            .groupBy({ it.first }, { it.second })
        return TreeMap(grouped) // ascending by date
    }

    /** Convenience: load + group. */
    fun groupTripsByDate(context: Context): SortedMap<LocalDate, List<LookupRow>> =
        groupTripsByDate(load(context))

    /** Get all trips on a specific date. */
    fun tripsOn(date: LocalDate, rows: List<LookupRow>): List<LookupRow> =
        groupTripsByDate(rows)[date] ?: emptyList()

    /** Convenience: load + trips on a specific date. */
    fun tripsOn(context: Context, date: LocalDate): List<LookupRow> =
        groupTripsByDate(context)[date] ?: emptyList()


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

