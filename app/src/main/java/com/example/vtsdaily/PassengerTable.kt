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
import androidx.compose.ui.unit.dp
import java.time.LocalDate

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
    onTripReinstated: () -> Unit
) {
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToActOn by remember { mutableStateOf<Passenger?>(null) }
    var tripBeingEdited by remember { mutableStateOf<Passenger?>(null) }

    val visiblePassengers = when (viewMode) {
        TripViewMode.ACTIVE -> (passengers + insertedPassengers)
            .filterNot { CompletedTripStore.isTripCompleted(context, scheduleDate, it) }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.COMPLETED -> (passengers + insertedPassengers)
            .filter { CompletedTripStore.isTripCompleted(context, scheduleDate, it) }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.REMOVED -> RemovedTripStore.getRemovedTrips(context, scheduleDate)
            .map { Passenger(it.name, "", it.pickupAddress, it.dropoffAddress, it.typeTime, "") }
            .sortedBy { toSortableTime(it.typeTime) }
    }

    val removedReasonMap = if (viewMode == TripViewMode.REMOVED) {
        RemovedTripStore.getRemovedTrips(context, scheduleDate).associateBy {
            "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}"
        }
    } else emptyMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (viewMode == TripViewMode.REMOVED) 0.dp else 4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        visiblePassengers.forEach { passenger ->
            val labelColor = Color(0xFF1A73E8)
            val passengerKey = "${passenger.name}-${passenger.pickupAddress}-${passenger.dropoffAddress}-${passenger.typeTime}"
            val reasonText = when (removedReasonMap[passengerKey]?.reason) {
                TripRemovalReason.CANCELLED -> " (Cancelled)"
                TripRemovalReason.NO_SHOW -> " (No Show)"
                TripRemovalReason.REMOVED -> " (Removed)"
                else -> ""
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (viewMode == TripViewMode.ACTIVE) {
                                        if (passenger in insertedPassengers) {
                                            tripBeingEdited = passenger
                                        } else {
                                            if (passenger.phone.isNotBlank()) {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${passenger.phone}"))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (viewMode == TripViewMode.ACTIVE || viewMode == TripViewMode.REMOVED) {
                                        selectedPassenger = passenger
                                    }
                                }
                            ),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = passenger.typeTime,
                            modifier = Modifier.weight(1f),
                            color = labelColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = passenger.name + reasonText,
                            modifier = Modifier.weight(2f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold)
                            Text(passenger.pickupAddress, color = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold)
                            Text(passenger.dropoffAddress, color = Color.DarkGray)
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
                                    tint = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Trip Action Dialog
    if (passengerToActOn != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { passengerToActOn = null },
            title = { Text("Select Action") },
            text = { Text("What do you want to do with this trip?") },
            confirmButton = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    listOf(
                        "Complete" to TripRemovalReason.COMPLETED,
                        "No Show" to TripRemovalReason.NO_SHOW,
                        "Cancel" to TripRemovalReason.CANCELLED,
                        "Remove" to TripRemovalReason.REMOVED
                    ).forEachIndexed { index, (label, reason) ->
                        TextButton(
                            onClick = {
                                onTripRemoved(passengerToActOn!!, reason)
                                passengerToActOn = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label)
                        }
                        if (index < 3) Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { passengerToActOn = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
