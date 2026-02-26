package com.example.vtsdaily

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vtsdaily.ui.theme.ActiveColor
import com.example.vtsdaily.ui.theme.CompletedColor
import com.example.vtsdaily.ui.theme.PrimaryPurple
import com.example.vtsdaily.ui.theme.RemovedColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import jxl.Sheet
import com.example.vtsdaily.ui.theme.VtsGreen
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.vtsdaily.ui.components.VtsCard

private const val SHOW_ADD_TRIP = false
private val SANDBOX_DATE: LocalDate = LocalDate.of(2099, 1, 1)

private fun isTestScheduleDate(d: LocalDate): Boolean {
    return d.year >= 2090  // adjust if your test years differ
}

@Composable
fun PassengerApp(
    onLookupForName: (String) -> Unit,
    onDialerLaunched: () -> Unit
) {

    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    val defaultDate = getAvailableScheduleDates().firstOrNull() ?: LocalDate.now()
    var scheduleDate by rememberSaveable { mutableStateOf(defaultDate) }
    var sandboxMode by rememberSaveable { mutableStateOf(false) }
    val effectiveDate = if (sandboxMode) SANDBOX_DATE else scheduleDate

    var baseSchedule by remember(effectiveDate) {
        mutableStateOf(loadSchedule(context, effectiveDate))
    }
    val isTestDate = scheduleDate.year >= 2099

    val phoneBook = remember(baseSchedule) { buildPhoneBookFromSchedule(baseSchedule.passengers) }
    var insertedPassengers by remember(effectiveDate) {
        mutableStateOf(InsertedTripStore.loadInsertedTrips(context, effectiveDate))
    }


    var showInsertDialog by remember { mutableStateOf(false) }
    var scrollToBottom by remember { mutableStateOf(false) }
    var showDateListDialog by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(TripViewMode.ACTIVE) }

    val handleTripReinstated: (Passenger) -> Unit = { passenger ->
        RemovedTripStore.removeRemovedTrip(context, effectiveDate, passenger)
        baseSchedule = loadSchedule(context, effectiveDate)
        insertedPassengers = InsertedTripStore.loadInsertedTrips(context, effectiveDate)

    }


// Choose which list to show in the table:
// - Active/Removed -> today's schedule (as before)
// - Completed      -> the trips you marked completed (from CompletedTripStore), mapped to Passenger
    val passengersForTable = if (viewMode == TripViewMode.COMPLETED) {
        CompletedTripStore.getCompletedTrips(context, effectiveDate).map { ct ->
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

    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(VtsGreen)
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

        VtsCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Trip Status toggle (always clickable)
                 {

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
            scheduleDate = effectiveDate,
            viewMode = viewMode,
            context = context,
            onTripRemoved = { removedPassenger, reason ->
                when (reason) {
                    TripRemovalReason.COMPLETED ->
                        CompletedTripStore.addCompletedTrip(context, effectiveDate, removedPassenger)
                    else -> {
                        RemovedTripStore.addRemovedTrip(context, effectiveDate, removedPassenger, reason)
                        InsertedTripStore.removeInsertedTrip(context, effectiveDate, removedPassenger)
                    }
                }

                baseSchedule = loadSchedule(context, effectiveDate)
                insertedPassengers = InsertedTripStore.loadInsertedTrips(context, effectiveDate)
            },
            onTripReinstated = handleTripReinstated,

            // ✅ Status chip cycles modes
            onToggleViewMode = {
                viewMode = when (viewMode) {
                    TripViewMode.ACTIVE -> TripViewMode.COMPLETED
                    TripViewMode.COMPLETED -> TripViewMode.REMOVED
                    TripViewMode.REMOVED -> TripViewMode.ACTIVE
                }
            },

            schedulePassengers = baseSchedule.passengers,
            phoneBook = phoneBook,
            onLookupForName = onLookupForName
        )


        if (showDateListDialog) {

            val pastDates = remember { getAvailableScheduleDates() }

            AlertDialog(
                onDismissRequest = { showDateListDialog = false },
                title = {
                    Text(
                        "Choose Date",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {

                    val listState = rememberLazyListState()

                    val firstRealIndex = remember(pastDates) {
                        val idx = pastDates.indexOfFirst { !isTestScheduleDate(it) }
                        if (idx >= 0) idx else 0
                    }

                    LaunchedEffect(pastDates) {
                        listState.scrollToItem(firstRealIndex)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pastDates) { date ->

                            TextButton(
                                onClick = {
                                    scheduleDate = date
                                    baseSchedule = loadSchedule(context, scheduleDate)
                                    insertedPassengers =
                                        InsertedTripStore.loadInsertedTrips(context, date)

                                    showDateListDialog = false
                                }
                            ) {
                                Text(
                                    date.format(
                                        DateTimeFormatter.ofPattern("MMMM d, yyyy")
                                    ),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDateListDialog = false }) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            )
        }
        if (SHOW_ADD_TRIP) {
            if (showInsertDialog) {
                InsertTripDialog(
                    onDismiss = { showInsertDialog = false },
                    onInsert = { newPassenger ->
                        insertedPassengers = insertedPassengers + newPassenger
                        InsertedTripStore.addInsertedTrip(context, effectiveDate, newPassenger)

                        showInsertDialog = false
                        scrollToBottom = true
                    }
                )
            }
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


