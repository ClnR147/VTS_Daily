package com.example.vtsdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import jxl.Workbook
import java.io.File
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Search
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialog  // ✅ Material 3 - correct
import com.example.vtsdaily.ui.theme.ActionGreen
import com.example.vtsdaily.ui.theme.CardHighlight
import com.example.vtsdaily.ui.theme.FromGrey
import com.example.vtsdaily.ui.theme.SubtleGrey
import com.example.vtsdaily.notes.TripNote
import com.example.vtsdaily.notes.TripNotesStore
import com.example.vtsdaily.notes.buildTripKey
import com.example.vtsdaily.notes.TripNoteDialog
import com.example.vtsdaily.notes.TripNoteBadges
import androidx.compose.material.icons.outlined.EditNote
import androidx.core.net.toUri


private var returnedFromDialer = false

@Composable
fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(4.dp)
    ) {
        Text(label)
    }
}

@Composable
fun PassengerContactDialog(
    name: String,
    phone: String,
    onCall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            if (phone.isNotBlank()) {
                Text(
                    text = phone,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "No phone on file",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            if (phone.isNotBlank()) {
                TextButton(onClick = {
                    onCall()
                    onDismiss()
                }) {
                    Text("Call")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassengerTable(
    passengers: List<Passenger>,
    insertedPassengers: List<Passenger>,
    setInsertedPassengers: (List<Passenger>) -> Unit,
    scheduleDate: LocalDate,
    viewMode: TripViewMode,
    context: Context,
    onTripRemoved: (Passenger, TripRemovalReason) -> Unit,
    onTripReinstated: (Passenger) -> Unit,
    onDialerLaunched: () -> Unit,
    onLookupForName: (String) -> Unit = {}   // ← NEW
) {
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var passengerToActOn by remember { mutableStateOf<Passenger?>(null) }
    var tripBeingEdited by remember { mutableStateOf<Passenger?>(null) }

    val tripNotesStore = remember { TripNotesStore(context) }

    var tripNotesMap by remember(scheduleDate) {
        mutableStateOf(tripNotesStore.load(scheduleDate.toString()))
    }

    var notePassenger by remember { mutableStateOf<Passenger?>(null) }
    var noteTripKey by remember { mutableStateOf<String?>(null) }


    fun tripKeyFor(p: Passenger): String {
        return buildTripKey(
            scheduleDateIso = scheduleDate.toString(),
            passengerId = p.id,
            name = p.name,
            pickupAddress = p.pickupAddress,
            dropoffAddress = p.dropoffAddress,
            typeTime = p.typeTime
        )
    }

    fun noteFor(p: Passenger): TripNote {
        val key = tripKeyFor(p)
        return tripNotesMap[key] ?: TripNote(tripKey = key)
    }

    fun launchWazeNavigation(context: Context, address: String) {
        val encoded = Uri.encode(address)
        val uri = "https://waze.com/ul?q=$encoded".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to open Waze.", Toast.LENGTH_SHORT).show()
        }
    }

    // Build name→phone from XLS (robust to missing headers); used for COMPLETED & REMOVED.
    val namePhones = remember(scheduleDate) { phoneNameMapFromXls(scheduleDate) }

    val visiblePassengers = when (viewMode) {
        TripViewMode.ACTIVE -> (passengers + insertedPassengers)
            .filterNot { CompletedTripStore.isTripCompleted(context, scheduleDate, it) }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.COMPLETED -> (passengers + insertedPassengers)
            .filter { CompletedTripStore.isTripCompleted(context, scheduleDate, it) }
            // 🔹 NEW: if phone missing, backfill from XLS by name-only (trim at '(' or '+', lowercase)
            .map { p ->
                if (p.phone.isNullOrBlank()) {
                    val key = cleanedName(p.name)
                    val lookedUp = namePhones[key]
                    if (!lookedUp.isNullOrBlank()) p.copy(phone = lookedUp) else p
                } else p
            }
            .sortedBy { toSortableTime(it.typeTime) }

        TripViewMode.REMOVED -> {
            RemovedTripStore.getRemovedTrips(context, scheduleDate)
                .map { rt ->
                    val key = cleanedName(rt.name)
                    val lookedUp = namePhones[key]
                    Passenger(
                        name = rt.name,
                        id = "",
                        pickupAddress = rt.pickupAddress,
                        dropoffAddress = rt.dropoffAddress,
                        typeTime = rt.typeTime,
                        phone = lookedUp ?: rt.phone.orEmpty()
                    )
                }
                .sortedBy { toSortableTime(it.typeTime) }
        }
    }

    val removedReasonMap = if (viewMode == TripViewMode.REMOVED) {
        RemovedTripStore.getRemovedTrips(context, scheduleDate).associateBy {
            "${it.name}-${it.pickupAddress}-${it.dropoffAddress}-${it.typeTime}"
        }
    } else emptyMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SubtleGrey)
            .padding(top = if (viewMode == TripViewMode.REMOVED) 0.dp else 4.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Trip Note dialog (ACTIVE edit)
        val pNote = notePassenger
        val kNote = noteTripKey
        if (pNote != null && kNote != null) {
            val initial = tripNotesMap[kNote] ?: TripNote(tripKey = kNote)

            TripNoteDialog(
                passengerName = pNote.name,
                pickup = pNote.pickupAddress,
                dropoff = pNote.dropoffAddress,
                initial = initial,
                onDismiss = {
                    notePassenger = null
                    noteTripKey = null
                },
                onSave = { updated ->
                    try {
                        val newMap = tripNotesMap.toMutableMap()
                        newMap[updated.tripKey] = updated
                        tripNotesMap = newMap

                        tripNotesStore.save(scheduleDate.toString(), tripNotesMap)

                        notePassenger = null
                        noteTripKey = null
                    } catch (e: Throwable) {
                        Log.e("TripNotes", "FAILED saving trip notes for ${scheduleDate}", e)
                        Toast.makeText(
                            context,
                            "Trip notes save failed: ${e.javaClass.simpleName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }


        visiblePassengers.forEach { passenger ->
            val labelColor = CardHighlight
            val passengerKey =
                "${passenger.name}-${passenger.pickupAddress}-${passenger.dropoffAddress}-${passenger.typeTime}"
            val reasonText = when (removedReasonMap[passengerKey]?.reason) {
                TripRemovalReason.CANCELLED -> " (Cancelled)"
                TripRemovalReason.NO_SHOW -> " (No Show)"
                TripRemovalReason.REMOVED -> " (Removed)"
                else -> ""
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {

                    var showContactDialog by remember { mutableStateOf(false) }

                    if (showContactDialog) {
                        PassengerContactDialog(
                            name = passenger.name + reasonText,
                            phone = passenger.phone,
                            onCall = {
                                if (passenger.phone.isNotBlank()) {
                                    returnedFromDialer = true
                                    val intent = Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${passenger.phone}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "No phone number available",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDismiss = { showContactDialog = false }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .combinedClickable(
                                onClick = {
                                    if (viewMode == TripViewMode.ACTIVE) {
                                        if (passenger in insertedPassengers) {
                                            tripBeingEdited = passenger
                                        } else {
                                            if (passenger.phone.isNotBlank()) {
                                                val intent = Intent(
                                                    Intent.ACTION_DIAL,
                                                    Uri.parse("tel:${passenger.phone}")
                                                )
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "No phone number available",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    when (viewMode) {
                                        TripViewMode.ACTIVE -> selectedPassenger = passenger
                                        TripViewMode.REMOVED -> {
                                            if (scheduleDate == LocalDate.now()) {
                                                selectedPassenger = passenger
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Reinstatements are only allowed for today's schedule.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        // allow reinstatement from COMPLETED via long-press
                                        TripViewMode.COMPLETED -> {
                                            if (scheduleDate == LocalDate.now()) {
                                                selectedPassenger = passenger
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Reinstatements are only allowed for today's schedule.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = passenger.typeTime,
                            color = labelColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .width(120.dp)
                                .alignByBaseline()
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = passenger.name + reasonText,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline()
                                .clickable {
                                    // Show big readable name/phone for office staff
                                    showContactDialog = true
                                }
                        )

                        // Phone column for Completed & Removed
                        if (viewMode == TripViewMode.COMPLETED || viewMode == TripViewMode.REMOVED) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = formatPhone(passenger.phone),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .width(140.dp)
                                    .alignByBaseline()
                            )
                        }
                    }

                    // <-- this closes the Row that contains typeTime + name (+ phone)

                    // ✅ INSERT #4 RIGHT HERE
                    val tripNote = noteFor(passenger)
                    val hasAnyBadge =
                        tripNote.flags != com.example.vtsdaily.notes.TripNoteFlags()

                    if (hasAnyBadge) {
                        Spacer(modifier = Modifier.height(2.dp))
                        TripNoteBadges(
                            flags = tripNote.flags,
                            modifier = Modifier.padding(start = 132.dp) // aligns under name
                        )
                    }


                    Spacer(modifier = Modifier.height(1.dp))

                    Column(modifier = Modifier.padding(bottom = 1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "From:",
                                modifier = Modifier.width(52.dp),
                                color = FromGrey,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                passenger.pickupAddress,
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "To:",
                                modifier = Modifier.width(52.dp),
                                color = FromGrey,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                passenger.dropoffAddress,
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (viewMode == TripViewMode.ACTIVE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // NEW: magnifying glass to jump to Lookup prefilled with this passenger's name
                            IconButton(
                                onClick = {
                                    val safe = sanitizeName(passenger.name)   // <- from the helper we added
                                    if (safe.isNotBlank()) onLookupForName(safe)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Lookup this passenger"
                                )
                            }
                            Spacer(Modifier.width(6.dp))

// NEW: notes button (ACTIVE only)
                            IconButton(
                                onClick = {
                                    notePassenger = passenger
                                    noteTripKey = tripKeyFor(passenger)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EditNote,
                                    contentDescription = "Trip notes"
                                )
                            }

                            Spacer(Modifier.width(6.dp))

                            // Existing checkmark
                            IconButton(
                                onClick = { passengerToActOn = passenger },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Mark as completed or cancel",
                                    tint = ActionGreen
                                )
                            }
                        }
                    }

                }
            }
        }
    }

    if (passengerToActOn != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { passengerToActOn = null },
            title = { Text("Select Action") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ActionButton("Complete") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.COMPLETED)
                        passengerToActOn = null
                    }
                    ActionButton("No Show") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.NO_SHOW)
                        passengerToActOn = null
                    }
                    ActionButton("Cancel") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.CANCELLED)
                        passengerToActOn = null
                    }
                    ActionButton("Remove") {
                        onTripRemoved(passengerToActOn!!, TripRemovalReason.REMOVED)
                        passengerToActOn = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { passengerToActOn = null }) { Text("Cancel") }
            }
        )
    }

    if (selectedPassenger != null && viewMode == TripViewMode.ACTIVE) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Navigate with Waze") },
            text = { Text("Where do you want to go?") },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            launchWazeNavigation(context, selectedPassenger!!.pickupAddress)
                            selectedPassenger = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pickup Location") }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            launchWazeNavigation(context, selectedPassenger!!.dropoffAddress)
                            selectedPassenger = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Dropoff Location") }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPassenger = null }) { Text("Cancel") }
            }
        )
    }

    if (selectedPassenger != null && viewMode == TripViewMode.REMOVED) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Reinstate Trip?") },
            text = { Text("This will move the trip back to the Active list.") },
            confirmButton = {
                TextButton(onClick = {
                    onTripReinstated(selectedPassenger!!)
                    selectedPassenger = null
                }) { Text("Reinstate") }
            },
            dismissButton = {
                TextButton(onClick = { selectedPassenger = null }) { Text("Cancel") }
            }
        )
    }

    // 🔹 NEW: Reinstate from COMPLETED
    if (selectedPassenger != null && viewMode == TripViewMode.COMPLETED) {
        AlertDialog(
            onDismissRequest = { selectedPassenger = null },
            title = { Text("Reinstate Completed Trip?") },
            text = { Text("Move this trip back to the Active list and remove it from Completed?") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = CompletedTripStore.removeCompletedTrip(
                        context = context,
                        forDate = scheduleDate,
                        passenger = selectedPassenger!!
                    )
                    if (ok) {
                        onTripReinstated(selectedPassenger!!)
                        Toast.makeText(context, "Reinstated to Active.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Couldn’t find completed record to remove.", Toast.LENGTH_SHORT).show()
                    }
                    selectedPassenger = null
                }) { Text("Reinstate") }
            },
            dismissButton = {
                TextButton(onClick = { selectedPassenger = null }) { Text("Cancel") }
            }
        )
    }
}

/* ===================== XLS helpers ===================== */

// Trim at '(' or '+', then lowercase and trim spaces.
private fun cleanedName(raw: String): String {
    val cut = raw.indexOfAny(charArrayOf('(', '+'))
    val base = if (cut >= 0) raw.substring(0, cut) else raw
    return base.trim().lowercase()
}

private fun phoneNameMapFromXls(date: LocalDate): Map<String, String> {
    val formatter = DateTimeFormatter.ofPattern("M-d-yy")
    val wantedName = "VTS ${date.format(formatter)}.xls"
    val dir = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
    val file = File(dir, wantedName)

    fun isPhone(s: String): Boolean {
        if (s.isBlank()) return false
        val re = Regex("""\(?\d{3}\)?[.\-\s]?\d{3}[.\-\s]?\d{4}""")
        return re.containsMatchIn(s)
    }

    if (!dir.exists() || !file.exists()) return emptyMap()

    val wb = Workbook.getWorkbook(file)
    return try {
        val sheet = wb.getSheet(0) ?: return emptyMap()

        fun cell(r: Int, c: Int): String =
            if (r in 0 until sheet.rows && c in 0 until sheet.columns)
                sheet.getCell(c, r).contents.trim()
            else ""

        // Try header detection (row 0). If not present, treat row 0 as data.
        val headers = (0 until sheet.columns).map { c -> cell(0, c) }
        val nameHeaderIdx = headers.indexOfFirst { it.equals("Passenger", true) || it.equals("Name", true) }
        val phoneHeaderIdx = headers.indexOfFirst { it.contains("phone", true) }

        val usingHeaders = nameHeaderIdx >= 0 || phoneHeaderIdx >= 0
        val startRow = if (usingHeaders) 1 else 0
        val nameCol = if (usingHeaders && nameHeaderIdx >= 0) nameHeaderIdx else 0

        val out = LinkedHashMap<String, String>()
        for (r in startRow until sheet.rows) {
            val nameRaw = cell(r, nameCol)
            if (nameRaw.isBlank()) continue

            var phone = if (usingHeaders && phoneHeaderIdx >= 0) cell(r, phoneHeaderIdx) else ""
            if (phone.isBlank()) {
                for (c in 0 until sheet.columns) {
                    val v = cell(r, c)
                    if (isPhone(v)) { phone = v; break }
                }
            }
            if (phone.isBlank()) continue

            val key = cleanedName(nameRaw)
            if (key.isNotEmpty() && !out.containsKey(key)) out[key] = phone
        }
        out
    } finally {
        wb.close()
    }
}
