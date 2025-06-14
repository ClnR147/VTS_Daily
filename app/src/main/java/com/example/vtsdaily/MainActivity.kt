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
import java.time.format.DateTimeFormatter
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.OutlinedTextField





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

enum class TripRemovalReason {
    CANCELLED,
    NO_SHOW,
    REMOVED,
    COMPLETED
}
data class RemovedTrip(
    val name: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val typeTime: String,
    val date: String, // format "M-d-yy"
    val reason: TripRemovalReason = TripRemovalReason.CANCELLED // default for legacy entries
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

        fun addRemovedTrip(forDate: LocalDate, passenger: Passenger, reason: TripRemovalReason) {
            val file = getFile()
            val type = object : TypeToken<MutableMap<String, MutableList<RemovedTrip>>>() {}.type
            val map: MutableMap<String, MutableList<RemovedTrip>> =
                if (file.exists()) gson.fromJson(file.readText(), type)
                else mutableMapOf()

            val key = forDate.format(formatter)
            val list = map.getOrPut(key) { mutableListOf() }

            list.add(
                RemovedTrip(
                    name = passenger.name,
                    pickupAddress = passenger.pickupAddress,
                    dropoffAddress = passenger.dropoffAddress,
                    typeTime = passenger.typeTime,
                    date = key,
                    reason = reason
                )
            )

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

    var insertedPassengers by remember(scheduleDate) {
        mutableStateOf(InsertedTripStore.loadInsertedTrips(context, scheduleDate))
    }

    var showInsertDialog by remember { mutableStateOf(false) }
    var scrollToBottom by remember { mutableStateOf(false) }
    var showDateListDialog by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(TripViewMode.ACTIVE) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {

        // Top Banner
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
                style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable { showDateListDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFFE0E0E0))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { if (viewMode == TripViewMode.ACTIVE) showInsertDialog = true },
                enabled = viewMode == TripViewMode.ACTIVE,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "AddTrip",
                    color = if (viewMode == TripViewMode.ACTIVE) Color(0xFF4A148C) else Color.Gray,
                    style = MaterialTheme.typography.h6
                        .copy(fontWeight = FontWeight.Medium)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            val statusLabel = when (viewMode) {
                TripViewMode.ACTIVE -> "Active"
                TripViewMode.COMPLETED -> "Completed"
                TripViewMode.REMOVED -> "NoShow/Cancel"
            }

            val statusColor = when (viewMode) {
                TripViewMode.ACTIVE -> Color(0xFF33691E)
                TripViewMode.COMPLETED -> Color(0xFF01579B)
                TripViewMode.REMOVED -> Color(0xFFEF6C00)
            }

            Text(
                text = "Trip Status:",
                color = Color(0xFF4A148C),
                style = MaterialTheme.typography.h6
                    .copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.clickable {
                    viewMode = when (viewMode) {
                        TripViewMode.ACTIVE -> TripViewMode.COMPLETED
                        TripViewMode.COMPLETED -> TripViewMode.REMOVED
                        TripViewMode.REMOVED -> TripViewMode.ACTIVE
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = statusLabel,
                color = statusColor,
                style = MaterialTheme.typography.h6
                    .copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date picker dialog
        if (showDateListDialog) {
            AlertDialog(
                onDismissRequest = { showDateListDialog = false },
                title = { Text("Choose Date", style = MaterialTheme.typography.h6
                ) },
                text = {
                    val pastDates = getAvailableScheduleDates()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pastDates.forEach { date ->
                            TextButton(onClick = {
                                scheduleDate = date
                                baseSchedule = loadSchedule(date)
                                insertedPassengers = InsertedTripStore.loadInsertedTrips(context, date)
                                showDateListDialog = false
                            }) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDateListDialog = false }) {
                        Text("Cancel", style = MaterialTheme.typography.body1)
                    }
                }
            )
        }

        // Insert trip dialog
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

        // Unified passenger table
        PassengerTable(
            passengers = baseSchedule.passengers,
            insertedPassengers = insertedPassengers,
            scheduleDate = scheduleDate,
            viewMode = viewMode,
            context = context,
            onTripRemoved = { removedPassenger, reason ->
                val formatter = DateTimeFormatter.ofPattern("M-d-yy")
                val key = "${removedPassenger.name}-${removedPassenger.pickupAddress}-${removedPassenger.dropoffAddress}-${removedPassenger.typeTime}-${scheduleDate.format(formatter)}"
                val prefs = context.getSharedPreferences("completedTrips", Context.MODE_PRIVATE)

                when (reason) {
                    TripRemovalReason.COMPLETED -> {
                        prefs.edit().putBoolean(key, true).apply()
                    }
                    else -> {
                        RemovedTripStore.addRemovedTrip(scheduleDate, removedPassenger, reason)
                        InsertedTripStore.removeInsertedTrip(context, scheduleDate, removedPassenger)
                    }
                }

                baseSchedule = loadSchedule(scheduleDate)
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




@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit
) {
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToActOn by remember { mutableStateOf<Passenger?>(null) }

    val prefs = context.getSharedPreferences("completedTrips", Context.MODE_PRIVATE)
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    val allPassengers = passengers + insertedPassengers
    val visiblePassengers = when (viewMode) {
        TripViewMode.ACTIVE -> allPassengers.filterNot {
            val key = "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}-${scheduleDate.format(formatter)}"
            prefs.getBoolean(key, false)
        }
        TripViewMode.COMPLETED -> allPassengers.filter {
            val key = "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}-${scheduleDate.format(formatter)}"
            val result = prefs.getBoolean(key, false)
            Log.d("FilterCompleted", "Checking key: $key => $result for ${it.name}")
            result
        }
        TripViewMode.REMOVED -> RemovedTripStore.getRemovedTrips(scheduleDate).map {
            Passenger(it.name, "", it.pickupAddress, it.dropoffAddress, it.typeTime, "")
        }
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

    if (passengerToActOn != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { passengerToActOn = null },
            title = { Text("Trip Action") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        passengerToActOn?.let { passenger ->
                            val key = "${passenger.name}-${passenger.pickupAddress}-${passenger.dropoffAddress}-${passenger.typeTime}-${scheduleDate.format(formatter)}"
                            Log.d("CompleteTrip", "Saving key: $key")
                            prefs.edit().putBoolean(key, true).apply()
                            Log.d("TripComplete", "Marked complete: $key")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Divider(color = Color(0xFF4285F4), thickness = 1.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Text("Time", modifier = Modifier.weight(1f), style = MaterialTheme.typography.h6
                , color = Color(0xFF1A237E))
            Text("Name", modifier = Modifier.weight(2f), style = MaterialTheme.typography.h6
                , color = Color(0xFF1A237E))
        }
        Divider(color = Color(0xFF4285F4), thickness = 1.5.dp)

        val removedReasonMap = if (viewMode == TripViewMode.REMOVED) {
            RemovedTripStore.getRemovedTrips(scheduleDate).associateBy {
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
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (passenger.phone.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${passenger.phone}")
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                onLongClick = {
                                    if (viewMode == TripViewMode.ACTIVE) {
                                        selectedPassenger = passenger
                                    }
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = passenger.typeTime,
                            modifier = Modifier.weight(1f),
                            color = labelColor,
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = passenger.name + reasonText,
                            modifier = Modifier.weight(2f),
                            style = MaterialTheme.typography.body1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.caption)
                            Text(passenger.pickupAddress, color = Color.DarkGray, style = MaterialTheme.typography.caption)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.caption)
                            Text(passenger.dropoffAddress, color = Color.DarkGray, style = MaterialTheme.typography.caption)
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
                    Text("Name, Pickup, Dropoff, and Time are required.", color = Color.Red, style = MaterialTheme.typography.caption)
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
