package com.example.vtsdaily

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@Composable
fun ManualEntryForm(onAdd: (Passenger) -> Unit) {
    val context = LocalContext.current

    var name by rememberSaveable { mutableStateOf(TextFieldValue("")) }
    var time by rememberSaveable { mutableStateOf(TextFieldValue("")) }
    var pickup by rememberSaveable { mutableStateOf(TextFieldValue("")) }
    var dropoff by rememberSaveable { mutableStateOf(TextFieldValue("")) }
    var phone by rememberSaveable { mutableStateOf(TextFieldValue("")) }

    val inputFmt = DateTimeFormatter.ofPattern("HH:mm")
    val displayFmt = DateTimeFormatter.ofPattern("h:mm a")

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Add Emergency Trip",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            label = { Text("Time (HH:mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = pickup,
            onValueChange = { pickup = it },
            label = { Text("Pickup Address") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dropoff,
            onValueChange = { dropoff = it },
            label = { Text("Dropoff Address") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            val parsedTime = try {
                LocalTime.parse(time.text.trim(), inputFmt)
            } catch (_: Exception) {
                Toast.makeText(context, "Invalid time format. Use HH:mm (e.g., 07:35).", Toast.LENGTH_SHORT).show()
                return@Button
            }

            if (name.text.isBlank()) {
                Toast.makeText(context, "Name is required.", Toast.LENGTH_SHORT).show()
                return@Button
            }

            val newPassenger = Passenger(
                name = name.text.trim(),
                id = "manual-${System.currentTimeMillis()}",
                pickupAddress = pickup.text.trim(),
                dropoffAddress = dropoff.text.trim(),
                typeTime = parsedTime.format(displayFmt),
                phone = phone.text.trim()
            )

            onAdd(newPassenger)
            Toast.makeText(context, "Trip added", Toast.LENGTH_SHORT).show()

            // Clear form
            name = TextFieldValue("")
            time = TextFieldValue("")
            pickup = TextFieldValue("")
            dropoff = TextFieldValue("")
            phone = TextFieldValue("")
        }) {
            Text("Add Trip")
        }
    }
}
