package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
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
import java.time.format.DateTimeFormatter
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay


// Data classes

data class Passenger(
    val name: String,
    val id: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val typeTime: String,
    val phone: String
)

data class Schedule(
    val date: String,
    val passengers: List<Passenger>
)

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

    companion object {

    }

}

fun loadSchedule(forDate: LocalDate): Schedule {
    val passengers = mutableListOf<Passenger>()
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val scheduleDate = forDate.format(formatter)

    try {
        val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
        val fileName = "VTS $scheduleDate.xls"
        val file = File(folder, fileName)
        if (!file.exists()) return Schedule(scheduleDate, emptyList())

        val workbook = Workbook.getWorkbook(file)
        val sheet = workbook.getSheet(0)

        for (i in 0 until sheet.rows) {
            val row = sheet.getRow(i)
            if (row.size >= 6 && row[0].contents.isNotBlank()) {
                passengers.add(
                    Passenger(
                        name = row[0].contents.trim(),
                        id = row[1].contents.trim(),
                        pickupAddress = row[2].contents.trim(),
                        dropoffAddress = row[3].contents.trim(),
                        typeTime = row[4].contents.trim(),
                        phone = row[5].contents.trim()
                    )
                )
            }
        }

        workbook.close()
    } catch (e: IOException) {
        e.printStackTrace()
    } catch (e: BiffException) {
        e.printStackTrace()
    }

    return Schedule(scheduleDate, passengers)
}

@Composable
fun PassengerApp() {
    var scheduleDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val baseSchedule = remember(scheduleDate) { loadSchedule(scheduleDate) }
    var insertedPassengers by rememberSaveable(scheduleDate) {
        mutableStateOf(emptyList<Passenger>()) }
    var showInsertDialog by remember { mutableStateOf(false) }
    var scrollToBottom by remember { mutableStateOf(false) }
    var showDateListDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {

            IconButton(
                onClick = { showInsertDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Insert new trip")
            }

            IconButton(onClick = { showDateListDialog = true }) {
            Icon(Icons.Default.List, contentDescription = "Select from available dates")
            }


            IconButton(onClick = {
                showCompleted = !showCompleted
            }) {
                Icon(
                    imageVector = if (showCompleted) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showCompleted) "Hide Completed" else "Show Completed"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (showDateListDialog) {
                AlertDialog(
                    onDismissRequest = { showDateListDialog = false },
                    title = { Text("Choose Date") },
                    text = {
                        val pastDates = getAvailableScheduleDates()

                        Column {
                            pastDates.forEach { date ->
                                TextButton(onClick = {
                                    showDateListDialog = false
                                    scheduleDate = date

                                }) {
                                    Text(date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showDateListDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showInsertDialog) {
                InsertTripDialog(
                    onDismiss = { showInsertDialog = false },
                    onInsert = { newPassenger ->
                        insertedPassengers = insertedPassengers + newPassenger
                        showInsertDialog = false
                        scrollToBottom = true
                    }

                )
            }



            Text(
                text = scheduleDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                style = MaterialTheme.typography.labelLarge
            )
        }

        PassengerTable(baseSchedule.passengers + insertedPassengers, baseSchedule.date, showCompleted)

        if (scrollToBottom) {
        LaunchedEffect(Unit) {
            delay(100)
            // Normally you'd scroll a LazyColumn here
            scrollToBottom = false
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(passengers: List<Passenger>, scheduleDate: String, showCompleted: Boolean) {
    val context = LocalContext.current
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToComplete by remember { mutableStateOf<Passenger?>(null) }

    val prefs = context.getSharedPreferences("completedTrips", Context.MODE_PRIVATE)

    val visiblePassengers = if (showCompleted) passengers else passengers.filterNot {
        val key = "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}-$scheduleDate"
        prefs.getBoolean(key, false)
    }

    if (selectedPassenger != null) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Navigate to...") },
            text = {
                Column {
                    Button(onClick = {
                        launchWaze(context, selectedPassenger!!.pickupAddress)
                        selectedPassenger = null
                    }) { Text("Pickup Address") }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        launchWaze(context, selectedPassenger!!.dropoffAddress)
                        selectedPassenger = null
                    }) { Text("Drop-off Address") }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (passengerToComplete != null && !showCompleted) {
        AlertDialog(
            onDismissRequest = { passengerToComplete = null },
            title = { Text("Complete Trip") },
            text = { Text("Mark this trip as completed and hide it?") },
            confirmButton = {
                TextButton(onClick = {
                    passengerToComplete?.let { passenger ->
                        val key = "${passenger.name}-${passenger.pickupAddress}-${passenger.dropoffAddress}-${passenger.typeTime}-$scheduleDate"
                        prefs.edit().putBoolean(key, true).apply()
                    }
                    passengerToComplete = null
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    passengerToComplete = null
                }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Time", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("Name", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
            Text("Trip", modifier = Modifier.weight(4f), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(32.dp))
        }

        visiblePassengers.forEachIndexed { index, passenger ->
            val backgroundColor = if (index % 2 == 0) Color(0xFFE8F4FD) else Color.White

            Column(modifier = Modifier.fillMaxWidth().background(backgroundColor)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                        .combinedClickable(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${passenger.phone}")
                                }
                                context.startActivity(intent)
                                },
                            onLongClick = {
                                selectedPassenger = passenger
                            }
                        )
                ) {
                    Text(passenger.typeTime, modifier = Modifier.weight(1f))
                    Text(passenger.name, modifier = Modifier.weight(2f))
                    Text("${passenger.pickupAddress} → ${passenger.dropoffAddress}", modifier = Modifier.weight(4f))

                    if (!showCompleted) {
                        IconButton(
                            onClick = { passengerToComplete = passenger },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Mark as completed",
                                tint = Color.Green
                            )
                        }
                    }

                }
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }
}

fun launchWaze(context: Context, address: String) {
    val encoded = Uri.encode(address)
    val uri = Uri.parse("https://waze.com/ul?q=$encoded&navigate=yes")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.waze")
    }
    try {
        context.startActivity(intent)

    } catch (e: Exception) {
        Toast.makeText(context, "Waze not installed", Toast.LENGTH_SHORT).show()
    }
}


fun getAvailableScheduleDates(): List<LocalDate> {
    val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
    if (!folder.exists()) return emptyList()

    val pattern = Regex("""VTS (\d{1,2}-\d{1,2}-\d{2})\.xls""")
    return folder.listFiles()
        ?.mapNotNull { file ->
            pattern.find(file.name)?.groupValues?.get(1)?.let {
                runCatching {
                    LocalDate.parse(it, DateTimeFormatter.ofPattern("M-d-yy"))
                }.getOrNull()
            }
        }
        ?.sortedDescending()
        ?: emptyList()
}

@Composable
fun InsertTripDialog(onDismiss: () -> Unit, onInsert: (Passenger) -> Unit) {
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
                    Text("Name, Pickup, Dropoff, and Time are required.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
