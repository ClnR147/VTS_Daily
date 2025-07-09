package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.ui.TripReinstateHelper
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
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit
)
{
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToActOn by remember { mutableStateOf<Passenger?>(null) }
    var tripBeingEdited by remember { mutableStateOf<Passenger?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val sortedPassengers = (passengers + insertedPassengers)
        .sortedBy { toSortableTime(it.typeTime) }

    val visiblePassengers = when (viewMode) {
        TripViewMode.ACTIVE -> (passengers + insertedPassengers)
            .filterNot {
                CompletedTripStore.isTripCompleted(context, scheduleDate, it)
            }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.COMPLETED -> (passengers + insertedPassengers)
            .filter {
                CompletedTripStore.isTripCompleted(context, scheduleDate, it)
            }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.REMOVED -> RemovedTripStore.getRemovedTrips(context, scheduleDate)
            .map {
                Passenger(it.name, "", it.pickupAddress, it.dropoffAddress, it.typeTime, "")
            }
            .sortedBy { toSortableTime(it.typeTime) }
    }



    if (selectedPassenger != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Navigate to...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        launchWaze(context, selectedPassenger!!.pickupAddress)
                        selectedPassenger = null
                    }) { Text("Pickup Address") }

                    Button(onClick = {
                        launchWaze(context, selectedPassenger!!.dropoffAddress)
                        selectedPassenger = null
                    }) { Text("Drop-off Address") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedPassenger = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedPassenger != null && viewMode == TripViewMode.REMOVED) {
        val isToday = scheduleDate == LocalDate.now()

        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Reinstate Trip?") },
            text = {
                Text(
                    if (isToday)
                        "This will restore the trip to the active schedule."
                    else
                        "You can only reinstate trips for today's schedule."
                )
            },
            confirmButton = {
                if (isToday) {
                    TextButton(onClick = {
                        TripReinstateHelper.reinstateTrip(context, scheduleDate, selectedPassenger!!)
                        Toast.makeText(context, "Trip reinstated. Swipe down to refresh.", Toast.LENGTH_SHORT).show()
                        selectedPassenger = null
                    }) {
                        Text("Reinstate")
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



    if (passengerToActOn != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { passengerToActOn = null },
            title = { Text("Trip Action") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        passengerToActOn?.let { passenger ->
                            CompletedTripStore.addCompletedTrip(context, scheduleDate, passenger)
                            onTripRemoved(passenger, TripRemovalReason.COMPLETED)
                        }
                        passengerToActOn = null
                    }) { Text("Complete") }

                    TextButton(onClick = {
                        passengerToActOn?.let {
                            onTripRemoved(it, TripRemovalReason.CANCELLED)
                            Toast.makeText(context, "Trip cancelled", Toast.LENGTH_SHORT).show()
                        }
                        passengerToActOn = null
                    }) { Text("Cancel Trip") }

                    TextButton(onClick = {
                        passengerToActOn?.let {
                            onTripRemoved(it, TripRemovalReason.NO_SHOW)
                            Toast.makeText(context, "Marked as No Show", Toast.LENGTH_SHORT).show()
                        }
                        passengerToActOn = null
                    }) { Text("No Show") }

                    TextButton(onClick = {
                        passengerToActOn?.let {
                            onTripRemoved(it, TripRemovalReason.REMOVED)
                            Toast.makeText(context, "Trip removed by dispatch", Toast.LENGTH_SHORT).show()
                        }
                        passengerToActOn = null
                    }) { Text("Removed") }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
    if (tripBeingEdited != null) {
        val passenger = tripBeingEdited!!

        var id by remember { mutableStateOf(passenger.id) }
        var name by remember { mutableStateOf(passenger.name) }
        var typeTime by remember { mutableStateOf(passenger.typeTime) }
        var pickup by remember { mutableStateOf(passenger.pickupAddress) }
        var dropoff by remember { mutableStateOf(passenger.dropoffAddress) }

        AlertDialog(
            onDismissRequest = { tripBeingEdited = null },
            title = { Text("Edit Trip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("ID") }) // ← new field
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    OutlinedTextField(value = typeTime, onValueChange = { typeTime = it }, label = { Text("Time (PR/PA hh:mm)") })
                    OutlinedTextField(value = pickup, onValueChange = { pickup = it }, label = { Text("Pickup Address") })
                    OutlinedTextField(value = dropoff, onValueChange = { dropoff = it }, label = { Text("Drop-off Address") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updatedTrip = passenger.copy(
                        id = id,
                        name = name,
                        typeTime = typeTime,
                        pickupAddress = pickup,
                        dropoffAddress = dropoff
                    )

                    val updatedList = insertedPassengers.toMutableList()
                    val index = updatedList.indexOf(passenger)
                    if (index != -1) {
                        updatedList[index] = updatedTrip
                        setInsertedPassengers(updatedList) // ✅ Trigger recomposition
                        InsertedTripStore.overwriteInsertedTrips(context, scheduleDate, updatedList) // ✅ Save to disk
                    }


                    tripBeingEdited = null
                }) {
                    Text("Save")
                }
            }

            ,
            dismissButton = {
                TextButton(onClick = { tripBeingEdited = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {

        val removedReasonMap = if (viewMode == TripViewMode.REMOVED) {
            RemovedTripStore.getRemovedTrips(context, scheduleDate).associateBy {
                "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}"
            }
        } else emptyMap()


        visiblePassengers.forEachIndexed { index, passenger ->
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
                            )
                        ,
                        verticalAlignment = Alignment.Top // Align at top to support baseline alignment
                    ) {
                        Text(
                            text = passenger.typeTime,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline(),
                            color = labelColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = passenger.name + reasonText,
                            modifier = Modifier
                                .weight(2f)
                                .alignByBaseline(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }



                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(passenger.pickupAddress, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(passenger.dropoffAddress, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
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
}

