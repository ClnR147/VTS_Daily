package com.example.vtsdaily.data

import android.content.Context
import com.example.vtsdaily.model.ContactEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

object ContactStore {
    private const val FILE_NAME = "important-contacts.json"

    fun load(context: Context): List<ContactEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            Json.decodeFromString(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, entries: List<ContactEntry>) {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(Json.encodeToString(entries))
    }

    fun addOrUpdate(context: Context, entry: ContactEntry) {
        val contacts = load(context).toMutableList()
        val index = contacts.indexOfFirst { it.id == entry.id }
        if (index != -1) contacts[index] = entry else contacts.add(entry)
        save(context, contacts)
    }

    fun delete(context: Context, id: String) {
        val updated = load(context).filterNot { it.id == id }
        save(context, updated)
    }
}
