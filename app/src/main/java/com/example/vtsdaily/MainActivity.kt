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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

data class RemovedTrip(
    val name: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val typeTime: String,
    val date: String // same format as Schedule.date, e.g. "5-31-25"
)

enum class TripViewMode {
    ACTIVE, COMPLETED, REMOVED
}

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

        // 🧠 Get removed trips for this date
        val removedTrips = RemovedTripStore.getRemovedTrips(forDate)

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

                // ❌ Skip passengers that were removed
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

    return Schedule(scheduleDate, passengers)
}




class RemovedTripStore {
    companion object {
        private val gson = Gson()
        private val formatter = DateTimeFormatter.ofPattern("M-d-yy")

        private fun getFile(): File {
            val dir = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "removed_trips.json")
        }

        fun getRemovedTrips(forDate: LocalDate): List<RemovedTrip> {
            val file = getFile()
            if (!file.exists()) return emptyList()

            val type = object : TypeToken<Map<String, List<RemovedTrip>>>() {}.type
            val map: Map<String, List<RemovedTrip>> = gson.fromJson(file.readText(), type)
            return map[forDate.format(formatter)] ?: emptyList()
        }

        fun isTripRemoved(forDate: LocalDate, passenger: Passenger): Boolean {
            return getRemovedTrips(forDate).any {
                it.name == passenger.name &&
                        it.pickupAddress == passenger.pickupAddress &&
                        it.dropoffAddress == passenger.dropoffAddress &&
                        it.typeTime == passenger.typeTime
            }
        }

        fun addRemovedTrip(forDate: LocalDate, passenger: Passenger) {
            val file = getFile()
            val type = object : TypeToken<MutableMap<String, MutableList<RemovedTrip>>>() {}.type
            val map: MutableMap<String, MutableList<RemovedTrip>> =
                if (file.exists()) gson.fromJson(file.readText(), type)
                else mutableMapOf()

            val key = forDate.format(formatter)
            val list = map.getOrPut(key) { mutableListOf() }
            list.add(RemovedTrip(passenger.name, passenger.pickupAddress, passenger.dropoffAddress, passenger.typeTime, key))

            file.writeText(gson.toJson(map))
        }
    }
}


@Composable
fun PassengerApp() {
    val context = LocalContext.current
    val defaultDate = getAvailableScheduleDates().firstOrNull() ?: LocalDate.now()
    var scheduleDate by rememberSaveable { mutableStateOf(defaultDate) }

    var baseSchedule: Schedule by remember(scheduleDate) {
        mutableStateOf(loadSchedule(scheduleDate))
    }

    var insertedPassengers by rememberSaveable(scheduleDate) { mutableStateOf(emptyList<Passenger>()) }
    var showInsertDialog by remember { mutableStateOf(false) }
    var showDateListDialog by remember { mutableStateOf(false) }
    var scrollToBottom by remember { mutableStateOf(false) }

    var viewMode by rememberSaveable { mutableStateOf(TripViewMode.ACTIVE) }

    val showCompleted = viewMode == TripViewMode.COMPLETED


    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF4285F4))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            IconButton(onClick = { showInsertDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Insert", tint = Color.White)
            }

            IconButton(onClick = { showDateListDialog = true }) {
                Icon(Icons.Default.List, contentDescription = "Select Date", tint = Color.White)
            }

            IconButton(onClick = {
                viewMode = when (viewMode) {
                    TripViewMode.ACTIVE -> TripViewMode.COMPLETED
                    TripViewMode.COMPLETED -> TripViewMode.REMOVED
                    TripViewMode.REMOVED -> TripViewMode.ACTIVE
                }
            }) {
                val icon = when (viewMode) {
                    TripViewMode.ACTIVE -> Icons.Default.VisibilityOff
                    TripViewMode.COMPLETED -> Icons.Default.Check
                    TripViewMode.REMOVED -> Icons.Default.List
                }
                Icon(icon, contentDescription = "Toggle View Mode", tint = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = scheduleDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
            )
        }

        if (showDateListDialog) {
            AlertDialog(
                onDismissRequest = { showDateListDialog = false },
                title = { Text("Choose Date") },
                text = {
                    val pastDates = getAvailableScheduleDates()
                    Column {
                        pastDates.forEach { date ->
                            TextButton(onClick = {
                                scheduleDate = date
                                baseSchedule = loadSchedule(date)
                                showDateListDialog = false
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

        val displayedPassengers = when (viewMode) {
            TripViewMode.ACTIVE -> baseSchedule.passengers + insertedPassengers
            TripViewMode.COMPLETED -> baseSchedule.passengers + insertedPassengers
            TripViewMode.REMOVED -> RemovedTripStore.getRemovedTrips(scheduleDate).map {
                Passenger(it.name, "", it.pickupAddress, it.dropoffAddress, it.typeTime, "")
            }
        }

        PassengerTable(
            passengers = displayedPassengers,
            scheduleDate = scheduleDate,
            showCompleted = showCompleted,
            context = context,
            onTripRemoved = {
                baseSchedule = loadSchedule(scheduleDate)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(
    passengers: List<Passenger>,
    scheduleDate: LocalDate,
    showCompleted: Boolean,
    context: Context, // add this
    onTripRemoved: () -> Unit
)
 {
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToComplete by remember { mutableStateOf<Passenger?>(null) }

    val prefs = context.getSharedPreferences("completedTrips", Context.MODE_PRIVATE)

     val visiblePassengers = if (showCompleted) {
         passengers
     } else {
         passengers.filterNot {
             val key = "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}-${scheduleDate.format(DateTimeFormatter.ofPattern("M-d-yy"))}"
             prefs.getBoolean(key, false)
         }
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        RemovedTripStore.addRemovedTrip(scheduleDate, selectedPassenger!!)
                        onTripRemoved()
                        selectedPassenger = null
                        Toast.makeText(context, "Trip removed", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Remove Trip", color = Color.Red)
                    }
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

    if (passengerToComplete != null && !showCompleted) {
        AlertDialog(
            onDismissRequest = { passengerToComplete = null },
            title = { Text("Complete Trip") },
            text = { Text("Mark this trip as completed and hide it?") },
            confirmButton = {
                TextButton(onClick = {
                    passengerToComplete?.let { passenger ->
                        val key = "${passenger.name}-${passenger.pickupAddress}-${passenger.dropoffAddress}-${passenger.typeTime}-${scheduleDate.format(DateTimeFormatter.ofPattern("M-d-yy"))}"
                        prefs.edit().putBoolean(key, true).apply()
                    }
                    passengerToComplete = null
                    onTripRemoved() // 🚨 This triggers reloading the visible list
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Text("Time", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color(0xFF1A237E))
            Text("Name", modifier = Modifier.weight(2f), style = MaterialTheme.typography.titleMedium, color = Color(0xFF1A237E))
            Text("Trip", modifier = Modifier.weight(4f), style = MaterialTheme.typography.titleMedium, color = Color(0xFF1A237E))
        }
        Divider(color = Color(0xFF4285F4), thickness = 1.5.dp)


        visiblePassengers.forEachIndexed { index, passenger ->
            val backgroundColor = if (index % 2 == 0) Color(0xFFF8FBFF) else Color.White
            val labelColor = Color(0xFF1A73E8)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = passenger.typeTime,
                        modifier = Modifier.weight(1f),
                        color = labelColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = passenger.name,
                        modifier = Modifier.weight(2f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${passenger.pickupAddress} → ${passenger.dropoffAddress}",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )

                if (!showCompleted) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(
                            onClick = { passengerToComplete = passenger },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Mark as completed",
                                tint = Color(0xFF2E7D32)
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
