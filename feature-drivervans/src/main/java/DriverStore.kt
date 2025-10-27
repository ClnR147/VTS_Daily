package com.vts.drivervans

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object DriverStore {
    private var cache: List<Driver>? = null
    private val gson = Gson()
    private const val FILE_NAME = "drivers.json"

    private fun file(context: Context): File =
        File(context.getExternalFilesDir(null), FILE_NAME)

    fun load(context: Context): List<Driver> {
        cache?.let { return it }
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val type = object : TypeToken<List<Driver>>() {}.type
                val list: List<Driver> = gson.fromJson(f.readText(), type)
                cache = list
                return list
            }
        }
        val empty = emptyList<Driver>()
        cache = empty
        return empty
    }

    fun save(context: Context, list: List<Driver>) {
        cache = list
        val f = file(context)
        f.parentFile?.mkdirs()
        f.writeText(gson.toJson(list))
    }

    fun isEmpty(context: Context): Boolean = load(context).isEmpty()

    /** Replace an existing driver (by id) */
    fun update(context: Context, updated: Driver) {
        val newList = load(context).map { if (it.id == updated.id) updated else it }
        save(context, newList)
    }

    /** Insert if id not present; otherwise replace (useful for “Add or Edit”) */
    fun upsert(context: Context, driver: Driver) {
        val existing = load(context)
        val idx = existing.indexOfFirst { it.id == driver.id }
        val newList = if (idx >= 0) {
            existing.map { if (it.id == driver.id) driver else it }
        } else {
            existing + driver
        }
        save(context, newList)
    }

    /** Toggle active flag without changing other fields */
    fun toggleActive(context: Context, id: String) {
        val newList = load(context).map { d ->
            if (d.id == id) d.copy(active = !d.active) else d
        }
        save(context, newList)
    }
}
