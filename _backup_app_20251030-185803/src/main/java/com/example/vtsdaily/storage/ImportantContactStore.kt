package com.example.vtsdaily.storage

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ImportantContactStore {
    private const val INTERNAL_FILE = "ImportantContacts.json"
    private val gson = Gson()

    private fun internalFile(context: Context) = File(context.filesDir, INTERNAL_FILE)

    // Safer: app-scoped external dir (no special permission required).
    // Stays under: /Android/data/<package>/files/PassengerSchedules/ImportantContacts.json
    // If you truly need the legacy root folder, see the comment below.
    private fun externalFile(context: Context): File {
        val base: File = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "PassengerSchedules").apply { if (!exists()) mkdirs() }
        return File(dir, "ImportantContacts.json")
    }

    // If you must use legacy root (/storage/emulated/0/PassengerSchedules), swap externalFile() to:
    // private fun externalFileLegacy(): File {
    //     val base = Environment.getExternalStorageDirectory()
    //     val dir = File(base, "PassengerSchedules").apply { if (!exists()) mkdirs() }
    //     return File(dir, "ImportantContacts.json")
    // }

    fun load(context: Context): List<ImportantContact> = runCatching {
        val f = internalFile(context)
        if (!f.exists()) emptyList()
        else {
            val type = object : TypeToken<List<ImportantContact>>() {}.type
            gson.fromJson<List<ImportantContact>>(f.readText(), type) ?: emptyList()
        }
    }.getOrElse { emptyList() }

    private fun save(context: Context, list: List<ImportantContact>) {
        runCatching { internalFile(context).writeText(gson.toJson(list)) }
            .getOrElse { /* swallow; caller handles UI */ }
    }

    fun upsert(context: Context, c: ImportantContact) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.name.equals(c.name, ignoreCase = true) }
        if (idx >= 0) list[idx] = c else list.add(c)
        save(context, list)
    }

    fun delete(context: Context, name: String) {
        val list = load(context).filterNot { it.name.equals(name, ignoreCase = true) }
        save(context, list)
    }

    fun backupToExternal(context: Context): Boolean = runCatching {
        externalFile(context).writeText(gson.toJson(load(context)))
        true
    }.getOrDefault(false)

    fun restoreFromExternal(context: Context): Boolean = runCatching {
        val ext = externalFile(context)
        if (!ext.exists()) return false
        val type = object : TypeToken<List<ImportantContact>>() {}.type
        val restored: List<ImportantContact> = gson.fromJson(ext.readText(), type) ?: emptyList()
        save(context, restored)
        true
    }.getOrDefault(false)
}
