package com.example.vtsdaily


import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter



@Composable
fun PassengerApp() {
    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    val defaultDate = getAvailableScheduleDates().firstOrNull() ?: LocalDate.now()
    var scheduleDate by rememberSaveable { mutableStateOf(defaultDate) }

    // ✅ must be var to support reassignment
    var baseSchedule by remember(scheduleDate) {
        mutableStateOf(loadSchedule(context, scheduleDate))
    }

    var insertedPassengers by remember(scheduleDate) {
        mutableStateOf(InsertedTripStore.loadInsertedTrips(context, scheduleDate))
    }

    var showInsertDialog by remember { mutableStateOf(false) }
    var scrollToBottom by remember { mutableStateOf(false) }
    var showDateListDialog by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(TripViewMode.ACTIVE) }

    // ✅ This gets called when a trip is reinstated



    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {


        // ✅ Reinserted Date Header at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(0xFFE0E0E0))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = scheduleDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                color = Color(0xFF4A148C),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable { showDateListDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 🔄 Trip Status + Add Trip
        val statusLabel = when (viewMode) {
            TripViewMode.ACTIVE -> "Active"
            TripViewMode.COMPLETED -> "Completed"
            TripViewMode.REMOVED -> "No Show / Cancelled / Removed"
        }
        val statusColor = when (viewMode) {
            TripViewMode.ACTIVE -> Color(0xFF33691E)
            TripViewMode.COMPLETED -> Color(0xFF01579B)
            TripViewMode.REMOVED -> Color(0xFFEF6C00)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Trip Status label & current status on same line
            Row(
                modifier = Modifier
                    .clickable {
                        viewMode = when (viewMode) {
                            TripViewMode.ACTIVE -> TripViewMode.COMPLETED
                            TripViewMode.COMPLETED -> TripViewMode.REMOVED
                            TripViewMode.REMOVED -> TripViewMode.ACTIVE
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip Status:",
                    color = Color(0xFF4A148C),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    color = statusColor,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Right: Red + Add Trip control (only when active)
            if (viewMode == TripViewMode.ACTIVE) {
                val canAddTrip = !scheduleDate.isBefore(LocalDate.now())

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Add Trip button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = canAddTrip) { showInsertDialog = true }
                            .alpha(if (canAddTrip) 1f else 0.3f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Red, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Trip",
                            fontSize = 14.sp,
                            color = Color.Red
                        )
                    }

                    // Contacts button (always enabled)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, ImportantContactsActivity::class.java))
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Blue, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Contacts",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Contacts",
                            fontSize = 14.sp,
                            color = Color.Blue
                        )
                    }
                }
            }


        }


        Spacer(modifier = Modifier.height(12.dp))

        if (showDateListDialog) {
            AlertDialog(
                onDismissRequest = { showDateListDialog = false },
                title = { Text("Choose Date", style = MaterialTheme.typography.titleLarge) },
                text = {
                    val pastDates = getAvailableScheduleDates()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pastDates.forEach { date ->
                            TextButton(onClick = {
                                scheduleDate = date
                                baseSchedule = loadSchedule(context, date)
                                insertedPassengers = InsertedTripStore.loadInsertedTrips(context, date)
                                showDateListDialog = false
                            }) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDateListDialog = false }) {
                        Text("Cancel", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            )
        }

        if (showInsertDialog) {
            InsertTripDialog(
                onDismiss = { showInsertDialog = false },
                onInsert = { newPassenger ->
                    insertedPassengers = insertedPassengers + newPassenger
                    InsertedTripStore.addInsertedTrip(context, scheduleDate, newPassenger)
                    showInsertDialog = false
                    scrollToBottom = true
                }
            )
        }


        if (scrollToBottom) {
            LaunchedEffect(Unit) {
                delay(100)
                scrollToBottom = false
            }
        }
    }
}

