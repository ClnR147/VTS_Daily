package com.example.vtsdaily.lookup

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DateFilterBar(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit
) {
    // Use your existing date picker dialog if you have one; this is just a stub hook
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = { /* open your date picker; call onDateSelected(chosenDate) */ }) {
            Text(selectedDate?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) ?: "Select Date")
        }
        if (selectedDate != null) {
            TextButton(onClick = { onDateSelected(null) }) { Text("Clear") }
        }
    }
}

