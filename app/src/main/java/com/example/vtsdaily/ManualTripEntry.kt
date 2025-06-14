package com.example.vtsdaily

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.material.Text
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Button


@Composable
fun ManualEntryForm(onAdd: (Passenger) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(TextFieldValue()) }
    var time by remember { mutableStateOf(TextFieldValue()) }
    var pickup by remember { mutableStateOf(TextFieldValue()) }
    var dropoff by remember { mutableStateOf(TextFieldValue()) }
    var phone by remember { mutableStateOf(TextFieldValue()) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add Emergency Trip", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:mm)") })
        OutlinedTextField(value = pickup, onValueChange = { pickup = it }, label = { Text("Pickup Address") })
        OutlinedTextField(value = dropoff, onValueChange = { dropoff = it }, label = { Text("Dropoff Address") })
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val parsedTime = try {
                LocalTime.parse(time.text, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                Toast.makeText(context, "Invalid time format", Toast.LENGTH_SHORT).show()
                return@Button
            }

            val newPassenger = Passenger(
                name = name.text,
                id = "manual-${System.currentTimeMillis()}",
                pickupAddress = pickup.text,
                dropoffAddress = dropoff.text,
                typeTime = parsedTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                phone = phone.text
            )
            onAdd(newPassenger)
            Toast.makeText(context, "Trip added", Toast.LENGTH_SHORT).show()

            name = TextFieldValue()
            time = TextFieldValue()
            pickup = TextFieldValue()
            dropoff = TextFieldValue()
            phone = TextFieldValue()
        }) {
            Text("Add Trip")
        }
    }
}
