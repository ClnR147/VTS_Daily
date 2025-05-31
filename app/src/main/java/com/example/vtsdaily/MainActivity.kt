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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jxl.Workbook
import jxl.read.biff.BiffException
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        fun showReturnNotification(context: Context) {
            // No-op for now
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
    val context = LocalContext.current
    var scheduleDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var schedule by remember { mutableStateOf(loadSchedule(scheduleDate)) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Button(onClick = {
                schedule = loadSchedule(scheduleDate)
                Toast.makeText(context, "Schedule reloaded", Toast.LENGTH_SHORT).show()
            }) {
                Text("Reload Schedule")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = scheduleDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = {
                scheduleDate = if (scheduleDate == LocalDate.now())
                    LocalDate.now().plusDays(1)
                else
                    LocalDate.now()

                schedule = loadSchedule(scheduleDate)
            }) {
                Icon(Icons.Default.DateRange, contentDescription = "Toggle Date")
            }
        }

        PassengerTable(schedule.passengers, schedule.date)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(passengers: List<Passenger>, scheduleDate: String) {
    val context = LocalContext.current
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToComplete by remember { mutableStateOf<Passenger?>(null) }

    val prefs = context.getSharedPreferences("completedTrips", Context.MODE_PRIVATE)

    val visiblePassengers = passengers.filterNot {
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

    if (passengerToComplete != null) {
        AlertDialog(
            onDismissRequest = { passengerToComplete = null },
            title = { Text("Complete Trip") },
            text = { Text("Mark this trip as completed and hide it?") },
            confirmButton = {
                TextButton(onClick = {
                    val key = "${passengerToComplete!!.name}-${passengerToComplete!!.pickupAddress}-${passengerToComplete!!.dropoffAddress}-${passengerToComplete!!.typeTime}-$scheduleDate"
                    prefs.edit().putBoolean(key, true).apply()
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
                                MainActivity.showReturnNotification(context)
                            },
                            onLongClick = {
                                selectedPassenger = passenger
                            }
                        )
                ) {
                    Text(passenger.typeTime, modifier = Modifier.weight(1f))
                    Text(passenger.name, modifier = Modifier.weight(2f))
                    Text("${passenger.pickupAddress} → ${passenger.dropoffAddress}", modifier = Modifier.weight(4f))

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
        MainActivity.showReturnNotification(context)
    } catch (e: Exception) {
        Toast.makeText(context, "Waze not installed", Toast.LENGTH_SHORT).show()
    }
}
