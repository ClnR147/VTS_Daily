package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import jxl.Workbook
import java.io.File
import android.os.Environment
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialog  // ✅ Material 3 - correct
import com.example.vtsdaily.ui.theme.ActionGreen
import com.example.vtsdaily.ui.theme.CardHighlight
import com.example.vtsdaily.ui.theme.FromGrey
import com.example.vtsdaily.ui.theme.SubtleGrey
@Composable
fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(4.dp) // Remove weight
    ) {
        Text(label)
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    setInsertedPassengers: (List<Passenger>) -> Unit,
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit,
    onTripReinstated: (Passenger) -> Unit
) {
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToActOn by remember { mutableStateOf<Passenger?>(null) }
    var tripBeingEdited by remember { mutableStateOf<Passenger?>(null) }

    fun launchWazeNavigation(context: Context, address: String) {
        val encoded = Uri.encode(address)
        val uri = Uri.parse("https://waze.com/ul?q=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to open Waze.", Toast.LENGTH_SHORT).show()
        }
    }

    val visiblePassengers = when (viewMode) {
        TripViewMode.ACTIVE -> (passengers + insertedPassengers)
            .filterNot { CompletedTripStore.isTripCompleted(context, scheduleDate, it) }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.COMPLETED -> (passengers + insertedPassengers)
            .filter { CompletedTripStore.isTripCompleted(context, scheduleDate, it) }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.REMOVED -> {
            // Safe baseline: just render removed trips; do NOT try to populate phone here
            RemovedTripStore.getRemovedTrips(context, scheduleDate)
                .map { rt ->
                    Passenger(
                        name = rt.name,
                        id = "",
                        pickupAddress = rt.pickupAddress,
                        dropoffAddress = rt.dropoffAddress,
                        typeTime = rt.typeTime,
                        phone = rt.phone.orEmpty() // if your store has it; otherwise remains blank
                    )
                }
                .sortedBy { toSortableTime(it.typeTime) }
        }
    }

    val removedReasonMap = if (viewMode == TripViewMode.REMOVED) {
        RemovedTripStore.getRemovedTrips(context, scheduleDate).associateBy {
            "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}"
        }
    } else emptyMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubtleGrey)
            .padding(top = if (viewMode == TripViewMode.REMOVED) 0.dp else 4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        visiblePassengers.forEach { passenger ->
            val labelColor = CardHighlight
            val passengerKey =
                "${passenger.name}-${passenger.pickupAddress}-${passenger.dropoffAddress}-${passenger.typeTime}"
            val reasonText = when (removedReasonMap[passengerKey]?.reason) {
                TripRemovalReason.CANCELLED -> " (Cancelled)"
                TripRemovalReason.NO_SHOW -> " (No Show)"
                TripRemovalReason.REMOVED -> " (Removed)"
                else -> ""
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .combinedClickable(
                                onClick = {
                                    if (viewMode == TripViewMode.ACTIVE) {
                                        if (passenger in insertedPassengers) {
                                            tripBeingEdited = passenger
                                        } else {
                                            if (passenger.phone.isNotBlank()) {
                                                val intent = Intent(
                                                    Intent.ACTION_DIAL,
                                                    Uri.parse("tel:${passenger.phone}")
                                                )
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "No phone number available",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    when (viewMode) {
                                        TripViewMode.ACTIVE -> {
                                            selectedPassenger = passenger
                                        }
                                        TripViewMode.REMOVED -> {
                                            if (scheduleDate == LocalDate.now()) {
                                                selectedPassenger = passenger
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Reinstatements are only allowed for today's schedule.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = passenger.typeTime,
                            color = labelColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .width(120.dp)
                                .alignByBaseline()
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = passenger.name + reasonText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline()
                        )

                        // Phone column (Completed & Removed screens show this column; may be blank for Removed)
                        if (viewMode == TripViewMode.COMPLETED || viewMode == TripViewMode.REMOVED) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = formatPhone(passenger.phone),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .width(140.dp)
                                    .alignByBaseline()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    Column(modifier = Modifier.padding(bottom = 1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "From:",
                                modifier = Modifier.width(52.dp),
                                color = FromGrey,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                passenger.pickupAddress,
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "To:",
                                modifier = Modifier.width(52.dp),
                                color = FromGrey,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                passenger.dropoffAddress,
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (viewMode == TripViewMode.ACTIVE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { passengerToActOn = passenger },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Mark as completed or cancel",
                                    tint = ActionGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (passengerToActOn != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { passengerToActOn = null },
            title = { Text("Select Action") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ActionButton("Complete") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.COMPLETED)
                        passengerToActOn = null
                    }
                    ActionButton("No Show") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.NO_SHOW)
                        passengerToActOn = null
                    }
                    ActionButton("Cancel") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.CANCELLED)
                        passengerToActOn = null
                    }
                    ActionButton("Remove") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.REMOVED)
                        passengerToActOn = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { passengerToActOn = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedPassenger != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Navigate with Waze") },
            text = { Text("Where do you want to go?") },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            launchWazeNavigation(context, selectedPassenger!!.pickupAddress)
                            selectedPassenger = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pickup Location")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            launchWazeNavigation(context, selectedPassenger!!.dropoffAddress)
                            selectedPassenger = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dropoff Location")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPassenger = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedPassenger != null && viewMode == TripViewMode.REMOVED) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Reinstate Trip?") },
            text = { Text("This will move the trip back to the Active list.") },
            confirmButton = {
                TextButton(onClick = {
                    onTripReinstated(selectedPassenger!!)
                    selectedPassenger = null
                }) {
                    Text("Reinstate")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPassenger = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ===== Keep your original XLS reader available (unchanged) ===== */

private fun phoneLookupFromXls(date: LocalDate): Map<String, String> {
    fun keyOf(name: String, pu: String, d: String, t: String) =
        "${name.trim().lowercase()}|${pu.trim().lowercase()}|${d.trim().lowercase()}|${t.trim().lowercase()}"

    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val fileName = "VTS ${date.format(formatter)}.xls"
    val file = File(Environment.getExternalStorageDirectory(), "PassengerSchedules/$fileName")
    if (!file.exists()) return emptyMap()

    val wb = Workbook.getWorkbook(file)
    return try {
        val sheet = wb.getSheet(0)
        if (sheet.rows == 0) return emptyMap()

        // read header row (r = 0)
        val headers = (0 until sheet.columns).map { c -> sheet.getCell(c, 0).contents.trim() }
        fun idxOf(vararg candidates: String): Int =
            headers.indexOfFirst { h -> candidates.any { cand -> h.equals(cand, ignoreCase = true) } }

        val nameIdx  = idxOf("Passenger", "Name")
        val puIdx    = idxOf("PAddress", "Pickup", "Pickup Address", "From")
        val doIdx    = idxOf("DAddress", "Dropoff", "Dropoff Address", "To")
        val timeIdx  = idxOf("PUTimeAppt", "Type/Time", "TypeTime", "Time", "Appt", "ApptTime")
        val phoneIdx = idxOf("Phone", "Phone Number", "Phone#", "Phone #")

        fun cell(r: Int, c: Int) = if (c >= 0) sheet.getCell(c, r).contents.trim() else ""

        buildMap<String, String> {
            for (r in 1 until sheet.rows) {
                val name = cell(r, nameIdx)
                if (name.isBlank()) continue
                val pu    = cell(r, puIdx)
                val d     = cell(r, doIdx)
                val t     = cell(r, timeIdx)
                val phone = cell(r, phoneIdx)
                if (phone.isNotBlank()) put(keyOf(name, pu, d, t), phone)
            }
        }
    } finally {
        wb.close()
    }
}

