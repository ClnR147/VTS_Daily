package com.example.vtsdaily.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ImportantContactStore {

    data class ImportReport(
        val kept: Int,
        val merged: Int,
        val added: Int,
        val dropped: Int,
        val errors: List<String> = emptyList()
    )

    private const val INTERNAL_FILE = "ImportantContacts.json"
    private val gson = Gson()

    private fun internalFile(context: Context) = File(context.filesDir, INTERNAL_FILE)

    // App-scoped external dir (no special permission). Path:
    // /Android/data/<package>/files/PassengerSchedules/ImportantContacts.json
    private fun externalFile(context: Context): File {
        val base: File = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "PassengerSchedules").apply { if (!exists()) mkdirs() }
        return File(dir, "ImportantContacts.json")
    }

    // ---------- Normalizers / Keys ----------
    private fun norm(s: String?) = s?.trim()?.lowercase() ?: ""
    private fun normPhone(p: String?) = p?.filter { it.isDigit() } ?: ""
    private fun keyOf(c: ImportantContact) = "${norm(c.name)}|${normPhone(c.phone)}"

    private fun mergePreferNonEmpty(base: ImportantContact, inc: ImportantContact): ImportantContact =
        base.copy(
            name = if (inc.name.isNotBlank()) inc.name else base.name,
            phone = if (inc.phone.isNotBlank()) inc.phone else base.phone,
            note  = if (inc.note.isNotBlank())  inc.note  else base.note
        )

    // ---------- Load / Save ----------
    fun load(context: Context): List<ImportantContact> = runCatching {
        val f = internalFile(context)
        if (!f.exists()) emptyList()
        else {
            val type = object : TypeToken<List<ImportantContact>>() {}.type
            gson.fromJson<List<ImportantContact>>(f.readText(), type) ?: emptyList()
        }
    }.getOrElse { emptyList() }

    // Keep public if you want bulk replace from UI; otherwise you can make this private.
    fun save(context: Context, list: List<ImportantContact>) {
        runCatching { internalFile(context).writeText(gson.toJson(list)) }
            .getOrElse { /* swallow; caller handles UI */ }
    }

    // ---------- CRUD (keyed by name+phone) ----------
    fun upsert(context: Context, c: ImportantContact) {
        val all = load(context).toMutableList()
        val k = keyOf(c)
        val idx = all.indexOfFirst { keyOf(it) == k }
        if (idx >= 0) {
            all[idx] = mergePreferNonEmpty(all[idx], c)
        } else {
            all += c
        }
        // stable order for UI
        all.sortBy { norm(it.name) }
        save(context, all)
    }

    fun delete(context: Context, name: String, phone: String) {
        val nName = norm(name)
        val nPhone = normPhone(phone)
        val newList = load(context).filterNot {
            norm(it.name) == nName && normPhone(it.phone) == nPhone
        }
        save(context, newList)
    }

    // ---------- Backup / Restore ----------
    fun backupToExternal(context: Context): Boolean = runCatching {
        externalFile(context).writeText(gson.toJson(load(context)))
        true
    }.getOrDefault(false)

    fun restoreFromExternal(context: Context): Boolean = runCatching {
        val ext = externalFile(context)
        if (!ext.exists()) return@runCatching false
        val type = object : TypeToken<List<ImportantContact>>() {}.type
        val restored: List<ImportantContact> = gson.fromJson(ext.readText(), type) ?: emptyList()
        save(context, restored)
        true
    }.getOrDefault(false)

    // ---------- Imports ----------
    fun importFromCsvFile(context: Context, source: File): ImportReport {
        if (!source.exists()) return ImportReport(0, 0, 0, 0, listOf("File not found"))
        val lines = runCatching { source.readLines(Charsets.UTF_8) }.getOrElse {
            return ImportReport(0, 0, 0, 0, listOf("Read failed: ${it.message}"))
        }
        if (lines.isEmpty()) return ImportReport(0, 0, 0, 0, listOf("Empty file"))

        var headerLine = lines.first()
        if (headerLine.isNotEmpty() && headerLine[0].code == 0xFEFF) headerLine = headerLine.substring(1)
        val rawHeader = parseCsvLineQuoted(headerLine)
        val header = rawHeader.map(::canonicalForHeader)

        fun get(map: Map<String, String>, key: String) = map[key].orEmpty().trim()

        val incoming = ArrayList<ImportantContact>(lines.size)
        var dropped = 0
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val fields = parseCsvLineQuoted(line).map { it.trim() }
            val values =
                if (fields.size > header.size) {
                    val merged = ArrayList<String>(header.size)
                    merged.addAll(fields.take(header.size - 1))
                    merged.add(fields.drop(header.size - 1).joinToString(","))
                    merged
                } else if (fields.size < header.size) {
                    fields + List(header.size - fields.size) { "" }
                } else fields

            val row = LinkedHashMap<String, String>(header.size)
            for (c in header.indices) row[header[c]] = values[c]

            val name = get(row, "name")
            val phone = get(row, "phone")
            val note = get(row, "note")

            // require at least a name or phone
            if (name.isBlank() && phone.isBlank()) { dropped++; continue }

            incoming += ImportantContact(name = name, phone = phone, note = note)
        }

        val report = mergeImported(context, incoming)
        return report.copy(dropped = report.dropped + dropped)
    }

    fun importFromJsonFile(context: Context, source: File): ImportReport {
        if (!source.exists()) return ImportReport(0, 0, 0, 0, listOf("File not found"))
        val errors = mutableListOf<String>()
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            List::class.java, ImportantContact::class.java
        ).type
        val incoming = runCatching {
            val json = source.readText(Charsets.UTF_8)
            gson.fromJson<List<ImportantContact>>(json, type) ?: emptyList()
        }.getOrElse {
            errors += "Invalid JSON: ${it.message}"
            emptyList()
        }
        val report = mergeImported(context, incoming)
        return report.copy(errors = report.errors + errors)
    }

    // ---------- Import helpers ----------
    private fun mergeImported(
        context: Context,
        incoming: List<ImportantContact>
    ): ImportReport {
        val current = load(context).toMutableList()
        val map = LinkedHashMap<String, ImportantContact>(current.size + incoming.size)

        // seed with current
        current.forEach { map[keyOf(it)] = it }

        var merged = 0
        var added = 0
        var dropped = 0

        incoming.forEach { inc ->
            val k = keyOf(inc)
            val exist = map[k]
            if (exist == null) {
                map[k] = inc
                added++
            } else {
                val newer = mergePreferNonEmpty(exist, inc)
                if (newer != exist) merged++
                map[k] = newer
            }
        }

        val final = map.values.sortedBy { norm(it.name) }
        save(context, final)
        return ImportReport(
            kept = final.size,
            merged = merged,
            added = added,
            dropped = dropped,
            errors = emptyList()
        )
    }

    /** Quote-aware CSV split (supports commas in quotes and "" escapes). */
    private fun parseCsvLineQuoted(s: String): List<String> {
        val out = ArrayList<String>(8)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < s.length && s[i + 1] == '"') { sb.append('"'); i++ }
                    else { inQuotes = !inQuotes }
                }
                ch == ',' && !inQuotes -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /** Map flexible CSV headers to our fields. */
    private fun canonicalForHeader(h: String): String = when (h.trim().lowercase()) {
        "name", "contact", "full name" -> "name"
        "phone", "tel", "mobile" -> "phone"
        "note", "notes", "comment", "comments" -> "note"
        else -> h.trim().lowercase()
    }
}
