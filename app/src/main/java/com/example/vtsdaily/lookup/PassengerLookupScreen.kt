package com.example.vtsdaily.lookup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/* --- Screen flow (2 pages) --- */
private enum class Page { NAMES, DETAILS }

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

private fun groupTripsByDate(rows: List<LookupRow>): Map<LocalDate, List<LookupRow>> {
    return rows
        .mapNotNull { row ->
            parseDateOrNull(row.driveDate)?.let { it to row }
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it }) // newest → oldest
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLookupScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var page by rememberSaveable { mutableStateOf(Page.NAMES) }
    var query by rememberSaveable { mutableStateOf("") }

    // 1) Load persisted rows at startup
    var allRows by remember { mutableStateOf(LookupStore.load(context)) }

    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    // Distinct names matching query
    val nameList = remember(allRows, query) {
        val q = query.trim()
        allRows.asSequence()
            .filter { it.passenger.contains(q, ignoreCase = true) }
            .map { it.passenger.trim() }
            .distinct()
            .sorted()
            .toList()
    }

    // ALL trips for this passenger, newest → oldest
    val tripsForPassenger by remember(allRows, selectedName) {
        mutableStateOf(
            if (selectedName == null) emptyList() else
                allRows
                    .filter { it.passenger.equals(selectedName!!, ignoreCase = true) }
                    .sortedByDescending { parseDateOrNull(it.driveDate) ?: LocalDate.MIN }
        )
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
                .onSuccess { imported ->
                    // 2) MERGE + SAVE to disk, then refresh UI
                    val merged = LookupStore.mergeAndSave(context, allRows, imported)
                    allRows = merged
                    page = Page.NAMES
                    scope.launch { snackbar.showSnackbar("Imported ${imported.size} new rows (merged).") }
                }
                .onFailure { e ->
                    scope.launch { snackbar.showSnackbar("Import failed: ${e.message}") }
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Keep the Import CSV FAB on the bottom-end
            FloatingActionButton(
                onClick = { doImport() },
                containerColor = VtsGreen,
                contentColor = VtsCream
            ) { Icon(Icons.Filled.Upload, contentDescription = "Import CSV") }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        // Use a Box so we can overlay a Back FAB on DETAILS
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (page == Page.NAMES) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        singleLine = true,
                        label = { Text("Search name") }
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                }

                when (page) {
                    Page.NAMES -> {
                        // Compact list: small spacing, no "Latest"
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(nameList) { name ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 0.5.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                        .clickable {
                                            selectedName = name
                                            page = Page.DETAILS
                                        }
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Page.DETAILS -> {
                        val name = selectedName ?: ""

                        // Group passenger's trips by date
                        val groupedTrips = remember(allRows, selectedName) {
                            val trips = allRows.filter { it.passenger.equals(selectedName, ignoreCase = true) }
                            groupTripsByDate(trips)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp) // tightened
                        ) {
                            item {
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Header: passenger name + phone
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2
                                        )

                                        val phone = groupedTrips.values.flatten()
                                            .firstOrNull { it.phone.isNotBlank() }?.phone
                                        if (!phone.isNullOrBlank()) {
                                            Text(
                                                "Phone: $phone",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                                    context.startActivity(i)
                                                }
                                            )
                                        }
// Header → first detail divider (same spacing style)
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                                            thickness = 3.dp,
                                            color = VtsGreen
                                        )

                                        // 🔹 Grouped trips by date (newest → oldest)
                                        groupedTrips.entries.forEachIndexed { index, (date, trips) ->
                                            if (index > 0) {
                                                // Small VTSGreen divider between date groups
                                                HorizontalDivider(
                                                    modifier = Modifier
                                                        .padding(top = 8.dp, bottom = 0.dp, start = 12.dp, end = 12.dp),
                                                    thickness = 3.dp,
                                                    color = VtsGreen
                                                )
                                            }

                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(RowStripe)
                                                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp) // ← no bottom pad
                                            ) {
                                                Text(
                                                    "Date: ${formatDate(date)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                                Spacer(Modifier.height(4.dp))

                                                val labelIndent = 70.dp  // consistent column alignment

                                                trips.forEachIndexed { i, r ->
                                                    Row(Modifier.fillMaxWidth()) {
                                                        if (r.pAddress.isNotBlank()) {
                                                            Text(
                                                                text = "Pickup:",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.width(labelIndent)
                                                            )
                                                            Text(
                                                                text = r.pAddress,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }

                                                    Row(Modifier.fillMaxWidth()) {
                                                        if (r.dAddress.isNotBlank()) {
                                                            Text(
                                                                text = "Drop-off:",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.width(labelIndent)
                                                            )
                                                            Text(
                                                                text = r.dAddress,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }

                                                    // 👇 add space only between trips, not after the last one
                                                    if (i < trips.lastIndex) {
                                                        Spacer(Modifier.height(4.dp))
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
            }

            // FLOATING "Back to names" on DETAILS page (bottom-start)
            if (page == Page.DETAILS) {
                ExtendedFloatingActionButton(
                    onClick = { page = Page.NAMES },
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                    text = { Text("Back to names") },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}
