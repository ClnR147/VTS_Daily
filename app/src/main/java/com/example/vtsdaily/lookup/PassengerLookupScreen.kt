package com.example.vtsdaily.lookup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.example.vtsdaily.DateSelectActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.example.vtsdaily.ui.components.ScreenDividers

/* --- Style (match Drivers) --- */
private val VtsGreen = Color(0xFF4CAF50)
private val VtsCream = Color(0xFFFFF5E1)
private val RowStripe = Color(0xFFF7F5FA)


/* --- Screen flow (2 pages) --- */
private enum class Page { NAMES, DETAILS }

/* --- Date helpers --- */
private val dateFormats = listOf(
    "M/d/yyyy", "MM/dd/yyyy",
    "M/d/yy",   "MM/dd/yy",   // 2-digit year support
    "yyyy-MM-dd",
    "MMMM d, yyyy"
).map { DateTimeFormatter.ofPattern(it, Locale.US) }

private fun parseDateOrNull(s: String?): LocalDate? {
    val t = s?.trim().orEmpty()
    if (t.isEmpty()) return null
    for (fmt in dateFormats) {
        try { return LocalDate.parse(t, fmt) } catch (_: DateTimeParseException) {}
    }
    return null
}

private fun groupTripsByDate(rows: List<LookupRow>): Map<LocalDate, List<LookupRow>> {
    return rows
        .mapNotNull { row -> parseDateOrNull(row.driveDate)?.let { it to row } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it }) // newest → oldest
}

private fun formatDate(d: LocalDate) = d.format(dateFormats.first())

/* ---------- Import source logging ---------- */

private fun logImportStart(file: File) {

}

/* --- CSV line parser (handles quotes/commas/"" escapes) --- */

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

/* ---------- DEBUG: explain why rows would be rejected (no behavior change) ---------- */
private val CardGutter = 12.dp      // screen edge ↔ card edge
private val CardInner = 14.dp       // card edge ↔ content
private val EXPECTED_HEADER = listOf(
    "DriveDate","Passenger","A/R","PAddress","DAddress","PUTimeAppt","DOTimeAppt","RTTime","Phone"
)
private val REQUIRED_COLS = setOf("DriveDate","Passenger")

/** Use the same date parser this screen already uses */
private fun parseDateCurrentForDebug(raw: String?): LocalDate? = parseDateOrNull(raw)

/**
 * Reads the CSV using the same parseCsvLine() and logs exactly why rows
 * are being rejected by baseline rules: column count, required fields,
 * date parse, and trip type presence (A/R or tripType).
 */
private fun debugImportRejectionsFromFile(file: File, maxSamples: Int = 15) {
    if (!file.exists()) {

        return
    }
    file.bufferedReader(Charset.forName("UTF-8")).use { br ->
        var headerLine = br.readLine() ?: run {
        return
        }
        // strip BOM
        if (headerLine.isNotEmpty() && headerLine[0].code == 0xFEFF) headerLine = headerLine.substring(1)

        val header = parseCsvLine(headerLine).map { it.trim() }


        val missingExpected = EXPECTED_HEADER.filter { it !in header }
        if (missingExpected.isNotEmpty()) {

        }

        var total = 0
        var accepted = 0
        var rejected = 0
        val reasons = mutableMapOf<String, Int>()
        val samples = mutableListOf<String>()

        fun reason(r: String) { reasons[r] = (reasons[r] ?: 0) + 1 }

        var lineNo = 1
        while (true) {
            val line = br.readLine() ?: break
            lineNo++
            total++

            if (line.isBlank()) {
                reason("blank_line"); rejected++
                if (samples.size < maxSamples) samples += "L$lineNo: blank_line"
                continue
            }

            val fields = parseCsvLine(line).map { it.trim() }
            val colCount = fields.size
            if (colCount != header.size) {
                val hasQuotes = '"' in line
                val code = when {
                    hasQuotes && colCount > header.size -> "colcount_over_header_quotes_present"
                    colCount > header.size -> "colcount_over_header"
                    else -> "colcount_under_header"
                }
                reason(code); rejected++
                if (samples.size < maxSamples) {
                    samples += "L$lineNo: $code fields=$colCount header=${header.size} preview='${line.take(160)}'"
                }
                continue
            }

            // Build row map
            val row = header.indices.associate { h -> header[h] to fields[h] }

            // Required fields present?
            val missingReq = REQUIRED_COLS.filter { row[it].isNullOrEmpty() }
            if (missingReq.isNotEmpty()) {
                reason("missing_required:${missingReq.joinToString("+")}")
                rejected++
                if (samples.size < maxSamples)
                    samples += "L$lineNo: missing_required $missingReq | DriveDate='${row["DriveDate"]}' Passenger='${row["Passenger"]}'"
                continue
            }

            // DriveDate parse using this screen’s parser
            val parsedDate = parseDateCurrentForDebug(row["DriveDate"])
            if (parsedDate == null) {
                reason("date_parse_fail")
                rejected++
                if (samples.size < maxSamples)
                    samples += "L$lineNo: date_parse_fail raw='${row["DriveDate"]}'"
                continue
            }

            // Trip type present under either key?
            val tripType = row["A/R"].orEmpty().ifEmpty { row["tripType"].orEmpty() }
            if (tripType.isEmpty()) {
                reason("triptype_empty")
                rejected++
                if (samples.size < maxSamples)
                    samples += "L$lineNo: triptype_empty Passenger='${row["Passenger"]}'"
                continue
            }

            // If we got here, it would be accepted by baseline rules
            accepted++
        }


        samples.forEach {  }
    }
}

/* ---------- Header canonicalization shim (fix mismatched names) ---------- */

// Map variants to canonical importer names
private fun canonicalForHeader(h: String): String = when (h.trim()) {
    "tripType"   -> "A/R"
    "puTimeAppt" -> "PUTimeAppt"
    "rtTime"     -> "RTTime"
    // already-canonical (and other columns) pass through unchanged
    else         -> h.trim()
}

/**
 * Creates a temp CSV whose FIRST LINE header is rewritten to canonical names
 * expected by your importer. All data lines are copied verbatim.
 * If no change is needed, returns the original file.
 */
private fun canonicalizeCsvHeaderToTemp(src: File, context: android.content.Context): File {
    if (!src.exists()) return src

    val lines = src.readLines(Charsets.UTF_8)
    if (lines.isEmpty()) return src

    // parse + rewrite header row only
    val rawHeader = lines.first()
    val header = parseCsvLine(
        if (rawHeader.isNotEmpty() && rawHeader[0].code == 0xFEFF) rawHeader.substring(1) else rawHeader
    )
    val rewritten = header.map(::canonicalForHeader)

    val changed = header != rewritten
    if (!changed) {

        return src
    }

    // Write to temp in cache
    val temp = File(context.cacheDir, "CustomerLookup.canon.csv")


    FileOutputStream(temp, false).bufferedWriter(Charsets.UTF_8).use { w ->
        // join header safely (headers have no commas)
        w.append(rewritten.joinToString(","))
        w.append('\n')
        // copy remaining lines exactly as-is
        for (i in 1 until lines.size) {
            w.append(lines[i])
            if (i < lines.lastIndex) w.append('\n')
        }
    }
    return temp
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PassengerLookupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var page by rememberSaveable { mutableStateOf(Page.NAMES) }
    var query by rememberSaveable { mutableStateOf("") }

    // 1) Load persisted rows at startup
    var allRows by remember { mutableStateOf(LookupStore.load(context)) }

    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }

    // --- NEW: trip counts for entire dataset (name-normalized) ---
    val counts by remember(allRows) {
        mutableStateOf(
            allRows.asSequence()
                .mapNotNull { it.passenger?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .map { it.replace(Regex("\\s+"), " ").lowercase() }
                .groupingBy { it }
                .eachCount()
        )
    }
    fun tripCountFor(displayName: String): Int {
        val key = displayName.trim().replace(Regex("\\s+"), " ").lowercase()
        return counts[key] ?: 0
    }

    // Distinct names matching query (filtered, sorted)
    val nameList = remember(allRows, query) {
        val q = query.trim()
        allRows.asSequence()
            .map { it.passenger.orEmpty() }
            .filter { it.contains(q, ignoreCase = true) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }

    // --- NEW: group filtered names by first letter for sticky headers ---
    remember(nameList) {
        nameList.groupBy { name ->
            val c = name.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
            if (c != null && c in 'A'..'Z') c else '#'
        }.toSortedMap()
    }

    // ALL trips for this passenger, newest → oldest

    fun doImport() {
        val guesses = listOf(
            "/storage/emulated/0/PassengerSchedules/CustomerLookup.csv"
        ).map(::File)

        val found = guesses.firstOrNull { it.exists() }
        if (found == null) {
            scope.launch { snackbar.showSnackbar("Lookup .csv not found in default locations.") }
        } else {
            // Canonicalize header → debug → import
            logImportStart(found)
            val canonFile = canonicalizeCsvHeaderToTemp(found, context)
            debugImportRejectionsFromFile(canonFile)

            runCatching { importLookupCsv(canonFile) }
                .onSuccess { imported ->
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
        snackbarHost = { SnackbarHost(snackbar) }
        // FAB removed; Import now lives in the actions row below
    ) { padding ->
        // Use a Box so we can overlay a Back FAB on DETAILS
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {

                ScreenDividers.Thick()

                // Actions row: Import + Date icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {

                            try {
                                LookupStore.invalidateLookupCache()

                            } catch (t: Throwable) {

                            }
                            doImport()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VtsGreen)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }

                    IconButton(
                        onClick = {
                            val i = Intent(context, DateSelectActivity::class.java)
                            context.startActivity(i)
                        }
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Trips by Date")
                    }
                }

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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Header row
                            // Floating (sticky) header row
                            stickyHeader {
                                Surface(tonalElevation = 1.dp) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface) // keep opaque for stickies
                                            .zIndex(1f)                                    // optional: ensure it stays above rows
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Passenger Name",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "# of Trips",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        ScreenDividers.Thin()
                                    }
                                }
                            }


                            // Name rows
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
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = tripCountFor(name).toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Page.DETAILS -> { ... } // unchanged


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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ElevatedCard(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = CardGutter, vertical = 6.dp)
                                ) {
                                    Column(
                                        Modifier.padding(CardInner),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Header: passenger name + phone
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2
                                        )

                                        val phone = groupedTrips.values
                                            .flatten()
                                            .firstNotNullOfOrNull { it.phone?.trim()?.takeIf { p -> p.isNotBlank() } }
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
                                        ScreenDividers.Thick()

                                        // Grouped trips by date (newest → oldest)
                                        groupedTrips.entries.forEachIndexed { index, (date, trips) ->
                                            if (index > 0) {
                                                // Small VTSGreen divider between date groups
                                                ScreenDividers.Thin()
                                            }

                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(RowStripe)
                                                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp)
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
                                                        val pickup = r.pAddress.orEmpty()
                                                        if (pickup.isNotBlank()) {
                                                            Text(
                                                                text = "Pickup:",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.width(labelIndent)
                                                            )
                                                            Text(
                                                                text = pickup,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }

                                                    Row(Modifier.fillMaxWidth()) {
                                                        val drop = r.dAddress.orEmpty()
                                                        if (drop.isNotBlank()) {
                                                            Text(
                                                                text = "Drop-off:",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.width(labelIndent)
                                                            )
                                                            Text(
                                                                text = drop,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }

                                                    // space only between trips, not after the last one
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
                SmallFloatingActionButton(
                    onClick = { page = Page.NAMES },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    containerColor = VtsGreen,
                    contentColor = VtsCream
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
    }
}
