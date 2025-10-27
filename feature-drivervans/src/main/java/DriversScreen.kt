package com.vts.drivervans

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import jxl.Workbook

/* --- VTS Daily style tokens --- */
private val VtsBanner = Color(0xFF9A7DAB)     // Smoky Plum (kept in case you reuse)
private val VtsBannerText = Color(0xFFFFF5E1) // Warm cream
private val RowPaddingH = 12.dp
private val RowPaddingV = 10.dp

val PrimaryGreen = Color(0xFF4CAF50)
val CardHighlight = Color(0xFF1A73E8)
val labelColor = CardHighlight

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen() {
    val context = LocalContext.current
    var drivers by remember { mutableStateOf(DriverStore.load(context)) }
    var editing by remember { mutableStateOf<Driver?>(null) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Search (van + name only)
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(drivers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) drivers
        else drivers.filter { d ->
            d.name.lowercase().contains(q) ||
                    d.van.toString().lowercase().contains(q) // van-only, no phone
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Drivers",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            val file = File("/storage/emulated/0/PassengerSchedules/Drivers.xls")
                            if (!file.exists()) {
                                scope.launch { snackbarHostState.showSnackbar("❌ File not found: ${file.path}") }
                                return@TextButton
                            }
                            val imported = importDriversXls(file, prior = drivers)
                            if (imported.isNotEmpty()) {
                                DriverStore.save(context, imported)
                                drivers = imported
                                scope.launch { snackbarHostState.showSnackbar("✅ Imported ${imported.size} drivers") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("⚠️ No rows imported (check headers)") }
                            }
                        }
                    ) { Text("Import XLS", color = MaterialTheme.colorScheme.primary) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search bar (van + name only)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                label = { Text("Search by van or name") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotBlank()) {
                        TextButton(onClick = { query = "" }) { Text("Clear") }
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                /* ---------- Sticky Header (Van · Driver Name · Phone) ---------- */
                stickyHeader {
                    Surface(color = PrimaryGreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = RowPaddingH, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Van",
                                color = VtsBannerText,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(0.9f)
                            )
                            Text(
                                "Driver",
                                color = VtsBannerText,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(2.2f)
                            )
                            Text(
                                "Phone",
                                color = VtsBannerText,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1.5f),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                /* ---------- Rows (filtered) ---------- */
                itemsIndexed(filtered, key = { _, d -> d.id }) { _, d ->
                    val alpha = if (d.active) 1f else 0.55f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .combinedClickable(
                                onClick = { editing = d }, // ← open edit dialog
                                onLongClick = {
                                    val updated = drivers.map {
                                        if (it.id == d.id) it.copy(active = !it.active) else it
                                    }
                                    drivers = updated
                                    DriverStore.save(context, updated)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (d.active) "Driver deactivated" else "Driver activated"
                                        )
                                    }
                                }
                            )
                            .padding(horizontal = RowPaddingH, vertical = RowPaddingV)
                    ) {
                        // Row 1: Van · Driver Name · Phone (tap phone to dial)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = d.van.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = CardHighlight
                            )
                            Text(
                                text = d.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(2.2f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatPhone(d.phone),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .clickable(onClickLabel = "Dial") {
                                        openDialer(context, d.phone)
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Row 2 (detail): make/model (already includes year if importer set it)
                        Text(
                            text = d.makeModel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }

    // --- Edit dialog ---
    editing?.let { current ->
        EditDriverDialog(
            driver = current,
            onDismiss = { editing = null },
            onSave = { updated ->
                val newList = drivers.map { if (it.id == updated.id) updated else it }
                drivers = newList
                DriverStore.save(context, newList)
                editing = null
            }
        )
    }
}

/* ──────────────────────────────────────────────────────────────
   Edit dialog: name, phone, van, make/model (keeps your compact UI)
   ────────────────────────────────────────────────────────────── */
@Composable
private fun EditDriverDialog(
    driver: Driver,
    onSave: (Driver) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(driver.name) }
    var phone by remember { mutableStateOf(driver.phone) }
    var van by remember { mutableStateOf(driver.van.toString()) }
    var makeModel by remember { mutableStateOf(driver.makeModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Driver") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = van,
                    onValueChange = { van = it },
                    label = { Text("Van #") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = makeModel,
                    onValueChange = { makeModel = it },
                    label = { Text("Make / Model (incl. year if desired)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = driver.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    van = van.trim(),
                    makeModel = makeModel.trim()
                )
                onSave(updated)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


/* ---------------- helpers ---------------- */

private fun openDialer(context: android.content.Context, phoneRaw: String) {
    val digits = phoneRaw.filter(Char::isDigit)
    if (digits.isNotEmpty()) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$digits")
        }
        context.startActivity(intent)
    }
}

private fun formatPhone(phoneRaw: String): String {
    val d = phoneRaw.filter(Char::isDigit)
    return if (d.length == 10) {
        "(${d.substring(0, 3)}) ${d.substring(3, 6)}-${d.substring(6)}"
    } else {
        phoneRaw.trim()
    }
}

/* ---------------- XLS import (jxl) ---------------- */

private fun importDriversXls(file: File, prior: List<Driver>): List<Driver> {
    val priorById = prior.associateBy { it.id }
    val wb = Workbook.getWorkbook(file)
    try {
        val sheet = wb.getSheet(0) ?: return emptyList()
        val headerRow = 0

        val headers = mutableMapOf<String, Int>()
        for (c in 0 until sheet.columns) {
            val key = sheet.getCell(c, headerRow).contents.trim().lowercase()
            if (key.isNotBlank()) headers[key] = c
        }
        fun col(name: String) = headers.entries.find { it.key.contains(name) }?.value ?: -1

        val nameCol  = col("name")
        val vanCol   = col("van")
        val yearCol  = col("year")
        val makeCol  = col("make")
        val modelCol = col("model")
        val phoneCol = col("phone")

        val out = mutableListOf<Driver>()
        for (r in (headerRow + 1) until sheet.rows) {
            val name  = getCell(sheet, r, nameCol)
            val van = getCell(sheet, r, vanCol).trim() // Keep as String
            val yearText = getCell(sheet, r, yearCol).trim()
            val year = yearText.toIntOrNull()
            val make  = getCell(sheet, r, makeCol).trim()
            val model = getCell(sheet, r, modelCol).trim()
            val phone = getCell(sheet, r, phoneCol)

            if (name.isBlank() && van.isBlank() && phone.isBlank()) continue

            val id = if (van.isNotBlank()) "van-$van"
            else UUID.nameUUIDFromBytes("$name|$year|$make|$model".toByteArray()).toString()

            val makeModelDisplay = buildString {
                if (yearText.isNotBlank()) append("$yearText ")
                append("$make $model".trim())
            }.trim()

            val prevActive = priorById[id]?.active ?: true

            out += Driver(
                id = id,
                van = van,
                name = name,
                phone = phone,
                year = year,
                makeModel = makeModelDisplay,
                active = prevActive
            )
        }
        return out
    } finally {
        wb.close()
    }
}

private fun getCell(s: jxl.Sheet, r: Int, c: Int): String =
    if (c >= 0 && r in 0 until s.rows && c < s.columns) s.getCell(c, r).contents.trim() else ""
