package com.example.vtsdaily.drivers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

object DriverStore {
    private const val FILE_NAME = "drivers.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<Driver> {
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return demo()
        val type = object : TypeToken<List<Driver>>() {}.type
        return runCatching { gson.fromJson<List<Driver>>(f.readText(), type) }.getOrElse { demo() }
    }

    fun save(context: Context, list: List<Driver>) {
        val f = file(context)
        val tmp = File(f.parentFile ?: f, f.name + ".tmp")
        tmp.writeText(gson.toJson(list))
        if (f.exists()) f.delete()
        tmp.renameTo(f)
    }

    fun upsert(context: Context, driver: Driver) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == driver.id }
        if (idx >= 0) list[idx] = driver else list.add(driver)
        save(context, list)
    }

    fun toggleActive(context: Context, id: String) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val d = list[idx]
            list[idx] = d.copy(active = !d.active)
            save(context, list)
        }
    }

    private fun demo(): List<Driver> = listOf(
        Driver(UUID.randomUUID().toString(), "Ray Alvarez", "805-555-0111", "964", 2022, "Ford Transit", true),
        Driver(UUID.randomUUID().toString(), "Dana Kim",    "805-555-0144", "951", 2020, "Ford Transit", true),
        Driver(UUID.randomUUID().toString(), "Luis Perez",  "805-555-0188", "947", 2021, "Ford Transit", false),
    )
}

