package com.example.vtsdaily.drivers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jxl.Workbook
import jxl.Sheet
import java.io.File

internal object DriverStore {
    private const val FILE_NAME = "drivers.json"
    private val gson = Gson()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<Driver> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val type = object : TypeToken<List<Driver>>() {}.type
        return runCatching { gson.fromJson<List<Driver>>(f.readText(), type) ?: emptyList() }
            .getOrElse { emptyList() }
    }

    fun save(context: Context, drivers: List<Driver>) {
        file(context).writeText(gson.toJson(drivers))
    }

    /** 🔹 NEW: delete by (name|phone) stable key; returns updated list */
    fun delete(context: Context, driver: Driver): List<Driver> {
        val delKey = keyOf(driver)
        val updated = load(context).filterNot { keyOf(it) == delKey }
        save(context, updated)
        return updated
    }

    private fun keyOf(d: Driver): String = keyOf(d.name, d.phone)
    private fun keyOf(name: String, phone: String?): String =
        "${name.trim().lowercase()}|${phone.orEmpty().trim().lowercase()}"

    /**
     * Import from .xls with headers: Name | Van | Year | Make | Model | Phone
     * - No id, no active.
     */
    fun importFromXls(xlsFile: File, sheetName: String? = null): List<Driver> {
        require(xlsFile.exists()) { "XLS not found: ${xlsFile.absolutePath}" }

        val wb = Workbook.getWorkbook(xlsFile)
        val sheet: Sheet = sheetName?.let { name ->
            wb.sheets.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Sheet '$name' not found in ${xlsFile.name}. Available: ${
                        wb.sheets.joinToString { it.name }
                    }"
                )
        } ?: wb.getSheet(0)

        if (sheet.rows == 0 || sheet.columns == 0) {
            wb.close(); return emptyList()
        }

        val headerIndex = buildHeaderIndex(sheet)
        val required = listOf("name","van","year","make","model","phone")
        val missing = required.filter { it !in headerIndex }
        if (missing.isNotEmpty()) {
            wb.close()
            throw IllegalArgumentException(
                "Missing columns: ${missing.joinToString()} — found: ${headerIndex.keys.joinToString()} (sheet '${sheet.name}')."
            )
        }

        val list = mutableListOf<Driver>()
        for (r in 1 until sheet.rows) {
            fun cell(key: String) = sheet.getCell(headerIndex[key]!!, r).contents?.trim().orEmpty()

            val name  = cell("name")
            val van   = cell("van")
            val year  = cell("year").toIntOrNull()
            val make  = cell("make")
            val model = cell("model")
            val phone = normalizePhone(cell("phone"))

            if (name.isBlank() && van.isBlank() && phone.isBlank() && make.isBlank() && model.isBlank()) continue

            list += Driver(
                name = name,
                van = van,
                year = year,
                make = make,
                model = model,
                phone = phone
            )
        }
        wb.close()

        // Dedupe by (name|van)
        val seen = HashSet<String>()
        return list.filter { d ->
            val k = "${d.name.trim().lowercase()}|${d.van.trim().lowercase()}"
            seen.add(k)   // add() returns true only the first time → keeps first occurrence
        }
    }

    private fun buildHeaderIndex(sheet: Sheet): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        fun norm(h: String) = h.trim().lowercase()
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")

        for (c in 0 until sheet.columns) {
            when (norm(sheet.getCell(c, 0).contents ?: "")) {
                "name"  -> map["name"] = c
                "van"   -> map["van"] = c
                "year"  -> map["year"] = c
                "make"  -> map["make"] = c
                "model" -> map["model"] = c
                "phone" -> map["phone"] = c
            }
        }
        return map
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "(${digits.substring(0,3)}) ${digits.substring(3,6)}-${digits.substring(6)}"
            digits.length == 11 && digits.startsWith("1") -> "(${digits.substring(1,4)}) ${digits.substring(4,7)}-${digits.substring(7)}"
            else -> raw.trim()
        }
    }
}
