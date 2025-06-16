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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CardDefaults
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale


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

val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)



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



fun toSortableTime(typeTime: String): LocalTime {
    return try {
        // Examples: "PA 09:00 - 10:00" → "09:00"
        val firstTime = typeTime.substringAfter(" ")
            .substringBefore("-")
            .trim()
        LocalTime.parse(firstTime, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        Log.e("SortError", "Failed to parse time from: $typeTime")
        LocalTime.MIDNIGHT
    }
}

fun loadSchedule(context: Context, scheduleDate: LocalDate): Schedule {
    val passengers = mutableListOf<Passenger>()
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val scheduleDateStr = scheduleDate.format(formatter) // Don't overwrite scheduleDate

    try {
        val folder = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
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





class RemovedTripStore {
    companion object {
        private val gson = Gson()
        private val formatter = DateTimeFormatter.ofPattern("M-d-yy")

        private fun getFile(context: Context, forDate: LocalDate): File {
            val formatter = DateTimeFormatter.ofPattern("M-d-yy")
            val dateFolder = forDate.format(formatter)
            val dir = File(context.filesDir, "removed-trips")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "$dateFolder.json")
        }


        fun getRemovedTrips(context: Context, forDate: LocalDate): List<RemovedTrip> {
            val file = getFile(context, forDate)
            if (!file.exists()) return emptyList()

            val type = object : TypeToken<List<RemovedTrip>>() {}.type
            return gson.fromJson(file.readText(), type)
        }

        fun isTripRemoved(context: Context, forDate: LocalDate, passenger: Passenger): Boolean {
            return getRemovedTrips(context, forDate).any {
                it.name == passenger.name &&
                        it.pickupAddress == passenger.pickupAddress &&
                        it.dropoffAddress == passenger.dropoffAddress &&
                        it.typeTime == passenger.typeTime
            }
        }


        fun addRemovedTrip(context: Context, forDate: LocalDate, passenger: Passenger, reason: TripRemovalReason) {
            val file = getFile(context, forDate)
            val type = object : TypeToken<MutableList<RemovedTrip>>() {}.type
            val list: MutableList<RemovedTrip> =
                if (file.exists()) gson.fromJson(file.readText(), type)
                else mutableListOf()

            list.add(
                RemovedTrip(
                    name = passenger.name,
                    pickupAddress = passenger.pickupAddress,
                    dropoffAddress = passenger.dropoffAddress,
                    typeTime = passenger.typeTime,
                    date = forDate.format(formatter),
                    reason = reason
                )
            )

            file.writeText(gson.toJson(list))
        }

    }
}



@Composable
fun PassengerApp() {
    val context = LocalContext.current

    val defaultDate = getAvailableScheduleDates().firstOrNull() ?: LocalDate.now()
    var scheduleDate by rememberSaveable { mutableStateOf(defaultDate) }

    var baseSchedule: Schedule by remember(scheduleDate) {
        mutableStateOf(loadSchedule(context, scheduleDate))
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
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
                    style = MaterialTheme.typography.titleMedium
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
                style = MaterialTheme.typography.titleMedium
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
                style = MaterialTheme.typography.titleMedium
                    .copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date picker dialog
        if (showDateListDialog) {
            AlertDialog(
                onDismissRequest = { showDateListDialog = false },
                title = { Text("Choose Date", style = MaterialTheme.typography.titleLarge
                ) },
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
            setInsertedPassengers = { insertedPassengers = it }, // ✅ Add this line
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    setInsertedPassengers: (List<Passenger>) -> Unit, // ✅ Add this
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit
)
 {
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToActOn by remember { mutableStateOf<Passenger?>(null) }
    var tripBeingEdited by remember { mutableStateOf<Passenger?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences("completedTrips", Context.MODE_PRIVATE)
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")

    val sortedPassengers = (passengers + insertedPassengers).sortedBy {toSortableTime(it.typeTime) }

    val visiblePassengers = when (viewMode) {
        TripViewMode.ACTIVE -> (passengers + insertedPassengers)
            .filterNot {
                val key = "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}-${scheduleDate.format(formatter)}"
                prefs.getBoolean(key, false)
            }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.COMPLETED -> (passengers + insertedPassengers)
            .filter {
                val key = "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}-${scheduleDate.format(formatter)}"
                prefs.getBoolean(key, false)
            }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.REMOVED -> RemovedTripStore.getRemovedTrips(context, scheduleDate)

            .map {
                Passenger(it.name, "", it.pickupAddress, it.dropoffAddress, it.typeTime, "")
            }
            .sortedBy { toSortableTime(it.typeTime) }
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
                            prefs.edit().putBoolean(key, true).apply()
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
    if (tripBeingEdited != null) {
        val passenger = tripBeingEdited!!

        var id by remember { mutableStateOf(passenger.id) }
        var name by remember { mutableStateOf(passenger.name) }
        var typeTime by remember { mutableStateOf(passenger.typeTime) }
        var pickup by remember { mutableStateOf(passenger.pickupAddress) }
        var dropoff by remember { mutableStateOf(passenger.dropoffAddress) }

        AlertDialog(
            onDismissRequest = { tripBeingEdited = null },
            title = { Text("Edit Trip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("ID") }) // ← new field
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    OutlinedTextField(value = typeTime, onValueChange = { typeTime = it }, label = { Text("Time (PR/PA hh:mm)") })
                    OutlinedTextField(value = pickup, onValueChange = { pickup = it }, label = { Text("Pickup Address") })
                    OutlinedTextField(value = dropoff, onValueChange = { dropoff = it }, label = { Text("Drop-off Address") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updatedTrip = passenger.copy(
                        id = id,
                        name = name,
                        typeTime = typeTime,
                        pickupAddress = pickup,
                        dropoffAddress = dropoff
                    )

                    val updatedList = insertedPassengers.toMutableList()
                    val index = updatedList.indexOf(passenger)
                    if (index != -1) {
                        updatedList[index] = updatedTrip
                        setInsertedPassengers(updatedList) // ✅ Trigger recomposition
                        InsertedTripStore.overwriteInsertedTrips(context, scheduleDate, updatedList) // ✅ Save to disk
                    }


                    tripBeingEdited = null
                }) {
                    Text("Save")
                }
            }

            ,
            dismissButton = {
                TextButton(onClick = { tripBeingEdited = null }) {
                    Text("Cancel")
                }
            }
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
            Text("Time", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall
                , color = Color(0xFF1A237E))
            Text("Name", modifier = Modifier.weight(2f), style = MaterialTheme.typography.titleSmall
                , color = Color(0xFF1A237E))
        }
        Divider(color = Color(0xFF4285F4), thickness = 1.5.dp)

        val removedReasonMap = if (viewMode == TripViewMode.REMOVED) {
            RemovedTripStore.getRemovedTrips(context, scheduleDate).associateBy {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (viewMode == TripViewMode.ACTIVE) {
                                        if (passenger in insertedPassengers) {
                                            tripBeingEdited = passenger
                                        } else {
                                            if (passenger.phone.isNotBlank()) {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${passenger.phone}"))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (viewMode == TripViewMode.ACTIVE) {
                                        selectedPassenger = passenger
                                    }
                                }
                            )
                        ,
                        verticalAlignment = Alignment.Top // Align at top to support baseline alignment
                    ) {
                        Text(
                            text = passenger.typeTime,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline(),
                            color = labelColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = passenger.name + reasonText,
                            modifier = Modifier
                                .weight(2f)
                                .alignByBaseline(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }



                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(passenger.pickupAddress, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To:", modifier = Modifier.width(52.dp), color = Color(0xFF5F6368), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(passenger.dropoffAddress, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
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
