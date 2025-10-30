package com.example.vtsdaily.lookup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/* --- Style (match Drivers) --- */
private val VtsGreen = Color(0xFF4CAF50)
private val VtsCream = Color(0xFFFFF5E1)
private val RowStripe = Color(0xFFF7F5FA)

/* --- Data --- */
data class LookupRow(
    val driveDate: String,
    val passenger: String,
    val pAddress: String,
    val dAddress: String,
    val phone: String
)

/* --- Screen flow --- */
private enum class Page { NAMES, DATES, DETAILS }

/* --- Date helpers --- */
private val dateFormats = listOf(
    DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("M/d/yy", Locale.US)
)
private fun parseDateOrNull(s: String): LocalDate? {
    val t = s.trim()
    for (f in dateFormats) {
        try { return LocalDate.parse(t, f) } catch (_: DateTimeParseException) {}
    }
    return null
}
private fun formatDate(d: LocalDate) = d.format(dateFormats.first())

/* --- CSV importer (headers: DriveDate, Passenger, PAddress, DAddress, Phone) --- */
private fun importLookupCsv(csv: File, charset: Charset = Charsets.UTF_8): List<LookupRow> {
    require(csv.exists()) { "CSV not found: ${csv.absolutePath}" }
    val lines = csv.readLines(charset).filter { it.isNotEmpty() }
    if (lines.isEmpty()) return emptyList()

    val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
    fun idx(name: String) = header.indexOf(name).takeIf { it >= 0 }
        ?: error("Missing column '$name' in CSV header: $header")

    val iDrive = idx("drivedate")
    val iName  = idx("passenger")
    val iPAddr = idx("paddress")
    val iDAddr = idx("daddress")
    val iPhone = idx("phone")

    val out = ArrayList<LookupRow>(lines.size - 1)
    for (ln in lines.drop(1)) {
        val cols = parseCsvLine(ln)
        if (cols.size <= maxOf(iDrive, iName, iPAddr, iDAddr, iPhone)) continue
        val row = LookupRow(
            driveDate = cols[iDrive].trim(),
            passenger = cols[iName].trim(),
            pAddress  = cols[iPAddr].trim(),
            dAddress  = cols[iDAddr].trim(),
            phone     = cols[iPhone].trim()
        )
        if (row.passenger.isNotBlank()) out += row
    }
    return out
}

/** Tiny CSV parser: handles quotes, commas, and "" escapes */
private fun parseCsvLine(s: String): List<String> {
    val out = ArrayList<String>()
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

/* --- UI --- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLookupScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var page by rememberSaveable { mutableStateOf(Page.NAMES) }
    var query by rememberSaveable { mutableStateOf("") }
    var allRows by remember { mutableStateOf<List<LookupRow>>(emptyList()) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    // Derived lists
    val nameList = remember(allRows, query) {
        val q = query.trim()
        allRows.asSequence()
            .filter { it.passenger.contains(q, ignoreCase = true) }
            .map { it.passenger.trim() }
            .distinct()
            .sorted()
            .toList()
    }
    val datesForName = remember(allRows, selectedName) {
        if (selectedName == null) emptyList() else
            allRows.asSequence()
                .filter { it.passenger.equals(selectedName!!, ignoreCase = true) }
                .mapNotNull { parseDateOrNull(it.driveDate) }
                .groupingBy { it }.eachCount()
                .toList()
                .sortedByDescending { it.first }  // newest → oldest
    }
    val detailsForDate = remember(allRows, selectedName, selectedDate) {
        if (selectedName == null || selectedDate == null) emptyList() else
            allRows.filter {
                it.passenger.equals(selectedName!!, ignoreCase = true) &&
                        parseDateOrNull(it.driveDate) == selectedDate
            }
    }

    fun doImport() {
        val guesses = listOf(
            "/storage/emulated/0/PassengerSchedules/CustomerLookup.csv"
        ).map(::File)

        val found = guesses.firstOrNull { it.exists() }
        if (found == null) {
            scope.launch { snackbar.showSnackbar("Lookup .csv not found in default locations.") }
        } else {
            runCatching { importLookupCsv(found) }
                .onSuccess {
                    allRows = it
                    page = Page.NAMES
                    scope.launch { snackbar.showSnackbar("Imported ${it.size} rows.") }
                }
                .onFailure { e ->
                    scope.launch { snackbar.showSnackbar("Import failed: ${e.message}") }
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { doImport() },
                containerColor = VtsGreen,
                contentColor = VtsCream
            ) { Icon(Icons.Filled.Upload, contentDescription = "Import CSV") }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Divider under the header, aligned to search bar width
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Search on the Names page only
            if (page == Page.NAMES) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text("Search name") }
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            when (page) {
                Page.NAMES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(nameList) { name ->
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        val latest = allRows.asSequence()
                                            .filter { it.passenger.equals(name, ignoreCase = true) }
                                            .mapNotNull { parseDateOrNull(it.driveDate) }
                                            .maxOrNull()
                                        if (latest != null) Text("Latest: ${formatDate(latest)}")
                                    },
                                    modifier = Modifier.clickable {
                                        selectedName = name
                                        selectedDate = null
                                        page = Page.DATES
                                    }
                                )
                            }
                        }
                    }
                }

                Page.DATES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(datesForName) { (date, count) ->
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(formatDate(date), fontWeight = FontWeight.SemiBold)
                                    },
                                    supportingContent = { Text("$count trip(s)") },
                                    modifier = Modifier.clickable {
                                        selectedDate = date
                                        page = Page.DETAILS
                                    }
                                )
                            }
                        }
                    }
                }

                Page.DETAILS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detailsForDate) { r ->
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(RowStripe)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Pickup: ${r.pAddress}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Drop-off: ${r.dAddress}", style = MaterialTheme.typography.bodyMedium)
                                    if (r.phone.isNotBlank()) {
                                        Text(
                                            r.phone,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.phone}"))
                                                context.startActivity(i)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
