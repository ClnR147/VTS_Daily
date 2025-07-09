package com.example.vtsdaily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun InsertTripDialog(
    onDismiss: () -> Unit,
    onInsert: (Passenger) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var pickup by remember { mutableStateOf("") }
    var dropoff by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val isValid = name.isNotBlank() && pickup.isNotBlank() && dropoff.isNotBlank() && time.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert New Trip") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("ID") })
                OutlinedTextField(value = pickup, onValueChange = { pickup = it }, label = { Text("Pickup Address") })
                OutlinedTextField(value = dropoff, onValueChange = { dropoff = it }, label = { Text("Dropoff Address") })
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })

                if (!isValid) {
                    Text(
                        text = "Name, Pickup, Dropoff, and Time are required.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newPassenger = Passenger(name, id, pickup, dropoff, time, phone)
                    onInsert(newPassenger)
                },
                enabled = isValid
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
