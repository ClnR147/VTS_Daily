package com.example.vtsdaily

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vtsdaily.ui.theme.ActiveColor
import com.example.vtsdaily.ui.theme.AppBackground
import com.example.vtsdaily.ui.theme.CompletedColor
import com.example.vtsdaily.ui.theme.PrimaryGreen
import com.example.vtsdaily.ui.theme.PrimaryPurple
import com.example.vtsdaily.ui.theme.RemovedColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import jxl.Sheet
import jxl.Workbook
import java.io.File
import android.os.Environment
import android.util.Log




@Composable
fun PassengerApp() {
    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    val defaultDate = getAvailableScheduleDates().firstOrNull() ?: LocalDate.now()
    var scheduleDate by rememberSaveable { mutableStateOf(defaultDate) }

    var baseSchedule by remember(scheduleDate) {
        mutableStateOf(loadSchedule(context, scheduleDate))
    }

    val phoneBook = remember(baseSchedule) { buildPhoneBookFromSchedule(baseSchedule.passengers) }
    var insertedPassengers by remember(scheduleDate) {
        mutableStateOf(InsertedTripStore.loadInsertedTrips(context, scheduleDate))
    }

    var showInsertDialog by remember { mutableStateOf(false) }
    var scrollToBottom by remember { mutableStateOf(false) }
    var showDateListDialog by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(TripViewMode.ACTIVE) }

    val handleTripReinstated: (Passenger) -> Unit = { passenger ->
        RemovedTripStore.removeRemovedTrip(context, scheduleDate, passenger)
        baseSchedule = loadSchedule(context, scheduleDate)
        insertedPassengers = InsertedTripStore.loadInsertedTrips(context, scheduleDate)
    }
// Choose which list to show in the table:
// - Active/Removed -> today's schedule (as before)
// - Completed      -> the trips you marked completed (from CompletedTripStore), mapped to Passenger
    val passengersForTable = if (viewMode == TripViewMode.COMPLETED) {
        CompletedTripStore.getCompletedTrips(context, scheduleDate).map { ct ->
            Passenger(
                name = ct.name,
                id = "", // not needed for display here
                pickupAddress = ct.pickupAddress,
                dropoffAddress = ct.dropoffAddress,
                typeTime = ct.typeTime,
                phone = ct.phone.orEmpty()
            )
        }
    } else {
        baseSchedule.passengers
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground) // light greenish background
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        // Date Header – centered text with consistent layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 0.dp), // was vertical = 12.dp
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = scheduleDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                color = PrimaryPurple,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clickable { showDateListDialog = true }
                    .padding(bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(PrimaryGreen)
                .height(1.dp)
        )



        val statusLabel = when (viewMode) {
            TripViewMode.ACTIVE -> "Active"
            TripViewMode.COMPLETED -> "Completed"
            TripViewMode.REMOVED -> "No Show / Cancelled / Removed"
        }
        val statusColor = when (viewMode) {
            TripViewMode.ACTIVE -> ActiveColor
            TripViewMode.COMPLETED -> CompletedColor
            TripViewMode.REMOVED -> RemovedColor
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Trip Status toggle (always clickable)
            Row(
                modifier = Modifier.clickable {
                    viewMode = when (viewMode) {
                        TripViewMode.ACTIVE -> TripViewMode.COMPLETED
                        TripViewMode.COMPLETED -> TripViewMode.REMOVED
                        TripViewMode.REMOVED -> TripViewMode.ACTIVE
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip Status:",
                    color = PrimaryPurple,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    color = statusColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp // 👈 adjust this to whatever size you want
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Right-side buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (viewMode == TripViewMode.ACTIVE) {
                    val canAddTrip = !scheduleDate.isBefore(LocalDate.now())
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = canAddTrip) { showInsertDialog = true }
                            .alpha(if (canAddTrip) 1f else 0.3f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.Red, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Trip", fontSize = 14.sp, color = Color.Red)
                    }
                }

                if (viewMode == TripViewMode.ACTIVE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    context,
                                    ImportantContactsActivity::class.java
                                )
                            )
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.Blue, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Contacts",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Contacts", fontSize = 14.sp, color = Color.Blue)
                    }
                }
            }
        }

        if (viewMode == TripViewMode.REMOVED) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        

        PassengerTableWithStaticHeader(
            passengers = passengersForTable,
            insertedPassengers = insertedPassengers,
            setInsertedPassengers = { insertedPassengers = it },
            scheduleDate = scheduleDate,
            viewMode = viewMode,
            context = context,
            onTripRemoved = { removedPassenger, reason ->
                val key = "${removedPassenger.name}-${removedPassenger.pickupAddress}-${removedPassenger.dropoffAddress}-${removedPassenger.typeTime}-${scheduleDate.format(formatter)}"

                when (reason) {
                    TripRemovalReason.COMPLETED -> CompletedTripStore.addCompletedTrip(context, scheduleDate, removedPassenger)
                    else -> {
                        RemovedTripStore.addRemovedTrip(context, scheduleDate, removedPassenger, reason)
                        InsertedTripStore.removeInsertedTrip(context, scheduleDate, removedPassenger)
                    }
                }

                baseSchedule = loadSchedule(context, scheduleDate)
                insertedPassengers = InsertedTripStore.loadInsertedTrips(context, scheduleDate)
            },
            onTripReinstated = handleTripReinstated,

            // 🔹 NEW: give the table access to the XLS passengers for phone lookup
            schedulePassengers = baseSchedule.passengers,
            phoneBook = phoneBook
        )

        if (showDateListDialog) {
            AlertDialog(
                onDismissRequest = { showDateListDialog = false },
                title = { Text("Choose Date", style = MaterialTheme.typography.titleLarge) },
                text = {
                    val pastDates = getAvailableScheduleDates()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp), // constrains height so it scrolls
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pastDates) { date ->
                            TextButton(onClick = {
                                scheduleDate = date
                                baseSchedule = loadSchedule(context, scheduleDate)
                                insertedPassengers = InsertedTripStore.loadInsertedTrips(context, date)
                                showDateListDialog = false
                            }) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                ,
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDateListDialog = false }) {
                        Text("Cancel", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            )

        }

        if (showInsertDialog) {
            InsertTripDialog(
                onDismiss = { showInsertDialog = false },
                onInsert = { newPassenger ->
                    insertedPassengers = insertedPassengers + newPassenger
                    InsertedTripStore.addInsertedTrip(context, scheduleDate, newPassenger)
                    showInsertDialog = false
                    scrollToBottom = true
                }
            )
        }

        if (scrollToBottom) {
            LaunchedEffect(Unit) {
                delay(100)
                scrollToBottom = false
            }
        }
    }
}

private fun headersOf(sheet: Sheet): List<String> =
    (0 until sheet.columns).map { c -> sheet.getCell(c, 0).contents.trim() }

fun formatPhone(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val d = raw.filter(Char::isDigit)
    return if (d.length == 10)
        "(${d.substring(0,3)}) ${d.substring(3,6)}-${d.substring(6)}"
    else raw
}
private fun baseName(n: String) =
    n.replace(Regex("\\s*\\(.*?\\)"), "") // strip (XLWC), (Cancelled), etc.
        .trim()


private fun idxOf(headers: List<String>, vararg candidates: String): Int =
    headers.indexOfFirst { h -> candidates.any { cand -> h.equals(cand, ignoreCase = true) } }

/**
 * Build a name->phone lookup from schedule passengers (if you have them).
 * Case/space insensitive match on passenger name.
 */
private fun buildPhoneBookFromSchedule(passengers: List<Passenger>?): Map<String, String> =
    passengers.orEmpty()
        .mapNotNull { p ->
            val key = p.name.trim().lowercase()
            val value = p.phone?.trim()?.takeIf { it.isNotEmpty() }
            if (key.isNotEmpty() && value != null) key to value else null
        }
        .toMap()
// Find a phone for a RemovedTrip by matching against XLS-loaded schedule passengers.
fun resolvePhoneFromSchedule(trip: RemovedTrip, schedulePassengers: List<Passenger>): String? {
    fun norm(s: String?) = s?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: ""
    val nName = norm(trip.name)
    val nPU   = norm(trip.pickupAddress)
    val nDO   = norm(trip.dropoffAddress)
    val nTT   = norm(trip.typeTime)

    val candidates = schedulePassengers.filter { p ->
        norm(p.name) == nName &&
                (nPU.isEmpty() || norm(p.pickupAddress)  == nPU) &&
                (nDO.isEmpty() || norm(p.dropoffAddress) == nDO)
    }

    // 1) Best: also matches type/time
    candidates.firstOrNull { norm(it.typeTime) == nTT }?.phone?.let { return it }

    // 2) Any candidate with a phone
    candidates.firstOrNull { !it.phone.isNullOrBlank() }?.phone?.let { return it }

    // 3) Fallback: match on name only
    return schedulePassengers.firstOrNull { norm(it.name) == nName && !it.phone.isNullOrBlank() }?.phone
}

/**
 * Load Completed trips from the same XLS.
 * - Looks for a sheet named "Completed" first; falls back to the first sheet if not found.
 * - Reads a "Phone" column if present; otherwise optionally uses schedulePhones to fill it.
 */
fun loadCompletedTrips(
    scheduleDate: LocalDate,
    schedulePhonesFromPassengers: List<Passenger>? = null
): List<CompletedTrip> {
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val scheduleDateStr = scheduleDate.format(formatter)

    val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
    val fileName = "VTS $scheduleDateStr.xls"
    val file = File(folder, fileName)
    if (!file.exists()) {
        Log.d("DEBUG", "Completed: file not found ${file.absolutePath}")
        return emptyList()
    }

    val wb = Workbook.getWorkbook(file)
    try {
        // Prefer a sheet literally named "Completed"; otherwise use the first sheet.
        val sheet = wb.sheets.firstOrNull { it.name.equals("Completed", ignoreCase = true) }
            ?: wb.getSheet(0)

        val headers = headersOf(sheet)

        val nameIdx   = idxOf(headers, "Passenger", "Name")
        val puIdx     = idxOf(headers, "PAddress", "Pickup", "Pickup Address")
        val doIdx     = idxOf(headers, "DAddress", "Dropoff", "Dropoff Address")
        val typeIdx   = idxOf(headers, "PUTimeAppt", "Type/Time", "TypeTime")
        val dateIdx   = idxOf(headers, "DriveDate", "Date")
        val doneIdx   = idxOf(headers, "CompletedAt", "Completed")
        val phoneIdx  = idxOf(headers, "Phone", "Phone Number") // optional

        fun cell(r: Int, c: Int): String =
            if (c >= 0) sheet.getCell(c, r).contents.trim() else ""

        val phoneBook = buildPhoneBookFromSchedule(schedulePhonesFromPassengers)

        val out = ArrayList<CompletedTrip>(sheet.rows.coerceAtLeast(1))
        for (r in 1 until sheet.rows) { // skip header row
            val name = cell(r, nameIdx)
            if (name.isEmpty()) continue

            val phoneFromCol = cell(r, phoneIdx).ifBlank { null }
            val phone = phoneFromCol ?: phoneBook[name.trim().lowercase()]

            out += CompletedTrip(
                name           = name,
                pickupAddress  = cell(r, puIdx),
                dropoffAddress = cell(r, doIdx),
                typeTime       = cell(r, typeIdx),
                date           = cell(r, dateIdx).ifEmpty { scheduleDateStr },
                completedAt    = cell(r, doneIdx).ifBlank { null },
                phone          = phone
            )
        }
        return out
    } finally {
        wb.close()
    }
}


