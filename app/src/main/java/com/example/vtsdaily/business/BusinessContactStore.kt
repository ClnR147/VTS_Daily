package com.example.vtsdaily.business

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BusinessContactStore {

    private const val FILE_NAME = "business_contacts.json"

    private fun file(appContext: Context): File =
        File(appContext.filesDir, FILE_NAME)

    fun load(appContext: Context): List<BusinessContact> {
        val f = file(appContext)
        if (!f.exists()) return emptyList()

        return runCatching {
            val text = f.readText()
            if (text.isBlank()) return@runCatching emptyList()

            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name", "").trim()
                    val address = o.optString("address", "").trim()
                    val phone = o.optString("phone", "").trim()
                    if (name.isNotEmpty()) {
                        add(BusinessContact(name = name, address = address, phone = phone))
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun upsert(appContext: Context, contact: BusinessContact) {
        val current = load(appContext).toMutableList()

        // Match ImportantContact behavior: treat (name, phone) as identity
        val idx = current.indexOfFirst {
            it.name.equals(contact.name, ignoreCase = true) && it.phone == contact.phone
        }

        if (idx >= 0) current[idx] = contact else current.add(contact)

        saveAll(appContext, current)
    }

    fun delete(appContext: Context, name: String, phone: String) {
        val current = load(appContext)
        val kept = current.filterNot { it.name.equals(name, ignoreCase = true) && it.phone == phone }
        saveAll(appContext, kept)
    }

    private fun saveAll(appContext: Context, contacts: List<BusinessContact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            val o = JSONObject().apply {
                put("name", c.name)
                put("address", c.address)
                put("phone", c.phone)
            }
            arr.put(o)
        }
        file(appContext).writeText(arr.toString(2))
    }
}
