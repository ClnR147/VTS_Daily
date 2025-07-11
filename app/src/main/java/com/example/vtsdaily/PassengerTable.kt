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
import androidx.compose.material3.AlertDialog  // ✅ Material 3 - correct



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
    onTripReinstated: (Passenger) -> Unit  // ✅ FIXED
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
        } catch (e: Exception) {
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
            .background(Color(0xFFF5F5F5)) // subtle gray to show card edges
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
                    .padding(horizontal = 6.dp, vertical = 4.dp), // 👈 vertical = 4.dp adds visible space
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
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${passenger.phone}"))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    when (viewMode) {
                                        TripViewMode.ACTIVE -> {
                                            selectedPassenger = passenger // triggers Waze dialog
                                        }
                                        TripViewMode.REMOVED -> {
                                            selectedPassenger = passenger // triggers reinstate confirmation dialog
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
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "From:",
                                modifier = Modifier.width(52.dp),
                                color = Color(0xFF5F6368),
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
                                color = Color(0xFF5F6368),
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
                                    tint = Color(0xFF2E7D32)
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


