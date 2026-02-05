// TripNoteDialog.kt
package com.example.vtsdaily.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.WheelchairPickup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripNoteDialog(
    passengerName: String,
    pickup: String,
    dropoff: String,
    initial: TripNote,
    onDismiss: () -> Unit,
    onSave: (TripNote) -> Unit
) {
    var flags by remember { mutableStateOf(initial.flags) }
    var gateCode by remember { mutableStateOf(initial.gateCode) }
    var noteText by remember { mutableStateOf(initial.noteText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trip Notes") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(passengerName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("PU: $pickup", style = MaterialTheme.typography.bodySmall)
                Text("DO: $dropoff", style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(12.dp))
                Text("Quick flags", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlagChip("Call", flags.callOnArrival) { flags = flags.copy(callOnArrival = it) }
                    FlagChip("Gate", flags.hasGateCode) { flags = flags.copy(hasGateCode = it) }
                    FlagChip("Ramp", flags.needsRamp) { flags = flags.copy(needsRamp = it) }
                    FlagChip("Lift", flags.needsLift) { flags = flags.copy(needsLift = it) }
                    FlagChip("Cane", flags.usesCane) { flags = flags.copy(usesCane = it) }
                    FlagChip("Car seat", flags.bringCarSeat) { flags = flags.copy(bringCarSeat = it) }
                    FlagChip("Front", flags.pickupFront) { flags = flags.copy(pickupFront = it) }
                    FlagChip("Back", flags.pickupBack) { flags = flags.copy(pickupBack = it) }
                    FlagChip("Alley", flags.pickupAlley) { flags = flags.copy(pickupAlley = it) }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = gateCode,
                    onValueChange = { gateCode = it },
                    label = { Text("Gate code (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = initial.copy(
                        flags = flags.copy(
                            // keep hasGateCode in sync if they typed something
                            hasGateCode = flags.hasGateCode || gateCode.isNotBlank()
                        ),
                        gateCode = gateCode.trim(),
                        noteText = noteText.trim(),
                        lastUpdatedEpochMs = System.currentTimeMillis()
                    )
                    onSave(updated)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FlagChip(label: String, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(label) }
    )
}

