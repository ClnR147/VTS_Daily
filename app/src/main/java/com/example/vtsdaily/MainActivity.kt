package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import java.io.File
import jxl.Workbook
import jxl.read.biff.BiffException
import java.io.IOException
import java.time.LocalDate
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CardDefaults
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Contacts
import com.example.vtsdaily.ui.TripReinstateHelper

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            MaterialTheme {
                PassengerApp()
            }
        }
    }
}





fun loadSchedule(context: Context, scheduleDate: LocalDate): Schedule {
    val passengers = mutableListOf<Passenger>()
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val scheduleDateStr = scheduleDate.format(formatter)  // ← move this up
    try {
        val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
        val formatter = DateTimeFormatter.ofPattern("M-d-yy")
        val scheduleDateStr = scheduleDate.format(formatter)

        val fileName = "VTS $scheduleDateStr.xls"
        val file = File(folder, fileName)
        if (!file.exists()) return Schedule(scheduleDateStr, emptyList())

        val workbook = Workbook.getWorkbook(file)
        val sheet = workbook.getSheet(0)

        val removedTrips = RemovedTripStore.getRemovedTrips(context, scheduleDate)

        for (i in 0 until sheet.rows) {
            val row = sheet.getRow(i)
            if (row.size >= 6 && row[0].contents.isNotBlank()) {
                val passenger = Passenger(
                    name = row[0].contents.trim(),
                    id = row[1].contents.trim(),
                    pickupAddress = row[2].contents.trim(),
                    dropoffAddress = row[3].contents.trim(),
                    typeTime = row[4].contents.trim(),
                    phone = row[5].contents.trim()
                )

                val isRemoved = removedTrips.any {
                    it.name == passenger.name &&
                            it.pickupAddress == passenger.pickupAddress &&
                            it.dropoffAddress == passenger.dropoffAddress &&
                            it.typeTime == passenger.typeTime
                }

                if (!isRemoved) {
                    passengers.add(passenger)
                }
            }
        }

        workbook.close()
    } catch (e: IOException) {
        e.printStackTrace()
    } catch (e: BiffException) {
        e.printStackTrace()
    }

    return Schedule(scheduleDateStr, passengers)
}


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
    val handleTripReinstated = {
        baseSchedule = loadSchedule(context, scheduleDate)
    }


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

        PassengerTableWithStaticHeader(
            passengers = baseSchedule.passengers,
            insertedPassengers = insertedPassengers,
            setInsertedPassengers = { insertedPassengers = it },
            scheduleDate = scheduleDate,
            viewMode = viewMode,
            context = context,
            onTripRemoved = { removedPassenger, reason ->
                val formatter = DateTimeFormatter.ofPattern("M-d-yy")
                val key = "${removedPassenger.name}-${removedPassenger.pickupAddress}-${removedPassenger.dropoffAddress}-${removedPassenger.typeTime}-${scheduleDate.format(formatter)}"

                when (reason) {
                    TripRemovalReason.COMPLETED -> {
                        CompletedTripStore.addCompletedTrip(context, scheduleDate, removedPassenger)
                    }
                    else -> {
                        RemovedTripStore.addRemovedTrip(context, scheduleDate, removedPassenger, reason)
                        InsertedTripStore.removeInsertedTrip(context, scheduleDate, removedPassenger)
                    }
                }

                baseSchedule = loadSchedule(context, scheduleDate)
                insertedPassengers = InsertedTripStore.loadInsertedTrips(context, scheduleDate)
            }
        )


        if (scrollToBottom) {
            LaunchedEffect(Unit) {
                delay(100)
                scrollToBottom = false
            }
        }
    }
}

@Composable
fun PassengerTableWithStaticHeader(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    setInsertedPassengers: (List<Passenger>) -> Unit,
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // 🔒 Static Header Row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(Color(0xFF9A7DAB)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Time",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFF5E1),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Name",
                modifier = Modifier.weight(2f),
                color = Color(0xFFFFF5E1),
                fontWeight = FontWeight.Bold
            )
        }

        // 🧾 Scrollable Passenger Rows (no header inside)
        PassengerTable(
            passengers = passengers,
            insertedPassengers = insertedPassengers,
            setInsertedPassengers = setInsertedPassengers,
            scheduleDate = scheduleDate,
            viewMode = viewMode,
            context = context,
            onTripRemoved = onTripRemoved
        )
    }
}

