package com.example.vtsdaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.lookup.LookupRow
import com.example.vtsdaily.lookup.LookupStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.vtsdaily.ui.theme.VtsGreen   // divider color
import com.example.vtsdaily.ui.components.ScreenDividers
import androidx.compose.runtime.CompositionLocalProvider
import com.example.vtsdaily.ui.theme.LocalCardDividerStyle
import com.example.vtsdaily.ui.theme.LocalScreenDividerStyle
import com.example.vtsdaily.ui.components.CardDividers
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.vtsdaily.prediction.HistoryTrip
import com.example.vtsdaily.prediction.PredictedTrip
import com.example.vtsdaily.prediction.PredictionEngine
import com.example.vtsdaily.prediction.TripType
import kotlin.math.roundToInt

class DateSelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // "history" (default) or "predict"
        val mode = intent?.getStringExtra("mode") ?: "history"

        setContent {
            // If you have an app theme like VtsDailyTheme, wrap with it here.
            // VtsDailyTheme {
            MaterialTheme {
                ProvideDividerStyles {
                    DateSelectScreen(mode = mode)
                }
            }
            // }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectScreen(mode: String = "history") {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isPredictMode = mode == "predict"

    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var trips by remember { mutableStateOf<List<LookupRow>>(emptyList()) }
    var predictedTrips by remember { mutableStateOf<List<PredictedTrip>>(emptyList()) }
    var dateDigits by rememberSaveable { mutableStateOf("") } // RAW DIGITS ONLY

    val onLoadDigits: (String) -> Unit = remember(context, snackbar, scope, mode) {
        { digits ->
            val parsed = parseDateDigits(digits)
            if (parsed == null) {
                scope.launch { snackbar.showSnackbar("Enter date as MMDDYY or MMDDYYYY") }
            } else {
                selectedDate = parsed

                if (!isPredictMode) {
                    // === HISTORY MODE (existing behavior) ===
                    val sorted = LookupStore.tripsOnChrono(LookupStore.load(context), parsed)
                    trips = sorted
                    predictedTrips = emptyList()
                    scope.launch {
                        snackbar.showSnackbar("Loaded ${sorted.size} trips for ${fmtDate(parsed)}")
                    }
                } else {
                    // === PREDICTION MODE ===
                    val predicted = PredictionEngine.predictScheduleForDate(
                        targetDate = parsed,
                        historySameWeekdayCount = 52,   // ~4 years of same-weekday history
                        loadHistoryTripsForDate = { historyDate ->
                            LookupStore
                                .tripsOnChrono(LookupStore.load(context), historyDate)
                                .map { row -> row.toHistoryTrip(historyDate) }
                        }
                    )
                    predictedTrips = predicted
                    trips = emptyList()
                    scope.launch {
                        snackbar.showSnackbar(
                            "Predicted ${predicted.size} trips for ${fmtDate(parsed)} (1    -year window)"
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isPredictMode) "Predict Schedule" else "Trips by Date"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { if (dateDigits.isNotBlank()) onLoadDigits(dateDigits) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = if (isPredictMode) "Re-predict" else "Reload"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // === Screen header divider (edge-to-edge in content area) ===
            ScreenDividers.Thick()

            // Content block padding
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Numbers-only field; slashes are drawn virtually
                    OutlinedTextField(
                        value = dateDigits,
                        onValueChange = { raw ->
                            dateDigits = raw.filter(Char::isDigit).take(8) // MMDDYY or MMDDYYYY
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Date (MMDDYY or MMDDYYYY)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onLoadDigits(dateDigits)
                            }
                        ),
                        visualTransformation = DigitsDateVisualTransformation()
                    )

                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onLoadDigits(dateDigits)
                    }) {
                        Text(if (isPredictMode) "Predict" else "Load")
                    }
                }

                if (selectedDate != null) {
                    val count = if (isPredictMode) predictedTrips.size else trips.size
                    Text(
                        text = "Selected: ${fmtDate(selectedDate!!)}  •  Trips: $count",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Thin divider before the list — match card width (parent already has 16.dp padding)
                ScreenDividers.Thin(inset = 0.dp)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (!isPredictMode) {
                        // === HISTORY LIST (existing UI) ===
                        itemsIndexed(trips) { index, r ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        r.passenger.orEmpty(),
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    val puAddr = r.pAddress.orEmpty()
                                    val doAddr = r.dAddress.orEmpty()
                                    if (puAddr.isNotBlank()) {
                                        Text(
                                            "Pickup: $puAddr",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (doAddr.isNotBlank()) {
                                        Text(
                                            "Drop-off: $doAddr",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    val tt = when (r.tripType?.lowercase()) {
                                        "appt" -> "Appt"
                                        "return" -> "Return"
                                        else -> if (!r.rtTime.isNullOrBlank()) "Return" else "Appt"
                                    }

                                    val puT = r.puTimeAppt.orEmpty()
                                    val doT = (r.doTimeAppt ?: r.raw["DOTimeAppt"]).orEmpty()
                                    val rtT = r.rtTime.orEmpty()

                                    val puDo = when {
                                        puT.isNotBlank() && doT.isNotBlank() -> "PU $puT \u2192 DO $doT"
                                        puT.isNotBlank() -> "PU $puT"
                                        doT.isNotBlank() -> "DO $doT"
                                        else -> ""
                                    }

                                    val line = listOfNotNull(
                                        tt.ifBlank { null },
                                        puDo.ifBlank { null },
                                        rtT.takeIf { it.isNotBlank() }?.let { "RT $it" }
                                    ).joinToString(" • ")

                                    Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            // === Detail card separator divider (same visual width as card) ===
                            if (index < trips.lastIndex) {
                                CardDividers.Thin(inset = 0.dp)
                            }
                        }
                    } else {
                        // === PREDICTED LIST ===
                        itemsIndexed(predictedTrips) { index, p ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Name + prediction strength
                                    val pct = (p.appearancePercent * 100).roundToInt()
                                    Text(
                                        "${p.passengerName}  (${pct}%)",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    val puAddr = p.pAddress
                                    val doAddr = p.dAddress
                                    if (puAddr.isNotBlank()) {
                                        Text(
                                            "Pickup: $puAddr",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (doAddr.isNotBlank()) {
                                        Text(
                                            "Drop-off: $doAddr",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    val tt = when (p.tripType) {
                                        TripType.APPT -> "Appt"
                                        TripType.RETURN -> "Return"
                                    }

                                    val puT = p.puTimeAppt?.toString().orEmpty()
                                    val rtT = p.rtTime?.toString().orEmpty()

                                    val line = when (p.tripType) {
                                        TripType.APPT -> listOfNotNull(
                                            tt,
                                            puT.takeIf { it.isNotBlank() }?.let { "PU $it" }
                                        ).joinToString(" • ")

                                        TripType.RETURN -> listOfNotNull(
                                            tt,
                                            rtT.takeIf { it.isNotBlank() }?.let { "RT $it" }
                                        ).joinToString(" • ")
                                    }

                                    Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (index < predictedTrips.lastIndex) {
                                CardDividers.Thin(inset = 0.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------- Divider style provider ---------------------- */
@Composable
private fun ProvideDividerStyles(content: @Composable () -> Unit) {
    val screen = LocalScreenDividerStyle.current
    val card = LocalCardDividerStyle.current

    CompositionLocalProvider(
        LocalScreenDividerStyle provides screen.copy(
            thin = 1.dp,
            medium = 3.dp,
            thick = 8.dp,
            horizontalInset = 16.dp,
            verticalSpace = 0.dp,
            color = VtsGreen
        ),
        LocalCardDividerStyle provides card.copy(
            thin = 1.dp,
            medium = 3.dp,
            thick = 3.dp,          // use 8.dp if you want a very bold in-card separator
            horizontalInset = 12.dp,
            verticalSpace = 8.dp,
            color = VtsGreen
        )
    ) {
        content()
    }
}

/* ---------------------- Visual transformation (digits -> "MM/DD/..") ---------------------- */
private class DigitsDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit)
        val out = buildString {
            when (digits.length) {
                0 -> {}
                1, 2 -> append(digits)
                3, 4 -> {
                    append(digits.substring(0, 2)); append('/')
                    append(digits.substring(2))
                }
                5, 6 -> {
                    append(digits.substring(0, 2)); append('/')
                    append(digits.substring(2, 4)); append('/')
                    append(digits.substring(4))
                }
                else -> {
                    val d = digits.take(8)
                    append(d.substring(0, 2)); append('/')
                    append(d.substring(2, 4)); append('/')
                    append(d.substring(4, 8))
                }
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1   // slash after MM
                offset <= 8 -> offset + 2   // slashes after MM and DD
                else -> out.length
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset == 3 -> 2
                offset <= 5 -> offset - 1
                offset == 6 -> 4
                offset <= out.length -> offset - 2
                else -> digits.length
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

/** Parse 6 or 8 digits (MMDDYY or MMDDYYYY) into LocalDate, returns null if invalid. */
private fun parseDateDigits(d: String): LocalDate? {
    val digits = d.filter(Char::isDigit)
    if (digits.length != 6 && digits.length != 8) return null
    val mm = digits.substring(0, 2).toIntOrNull() ?: return null
    val dd = digits.substring(2, 4).toIntOrNull() ?: return null
    val year = if (digits.length == 6) {
        (digits.substring(4, 6).toIntOrNull() ?: return null) + 2000
    } else {
        digits.substring(4, 8).toIntOrNull() ?: return null
    }
    return try { LocalDate.of(year, mm, dd) } catch (_: Exception) { null }
}

private fun fmtDate(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}/${d.year}"

/* ---------------------- LookupRow -> HistoryTrip mapping ---------------------- */

private fun LookupRow.toHistoryTrip(date: LocalDate): HistoryTrip {
    // Determine trip type
    val tt = tripType?.lowercase()
    val tripTypeEnum = when (tt) {
        "appt" -> TripType.APPT
        "return" -> TripType.RETURN
        else -> if (!rtTime.isNullOrBlank()) TripType.RETURN else TripType.APPT
    }

    return HistoryTrip(
        date = date,
        passengerName = passenger.orEmpty(),
        tripType = tripTypeEnum,
        puTimeAppt = parseTimeOrNull(puTimeAppt),
        rtTime = parseTimeOrNull(rtTime),
        pAddress = pAddress.orEmpty(),
        dAddress = dAddress.orEmpty()
    )
}

private fun parseTimeOrNull(s: String?): LocalTime? {
    if (s.isNullOrBlank()) return null
    val trimmed = s.trim()
    return try {
        // Basic support for HH:mm or HH:mm:ss
        LocalTime.parse(trimmed)
    } catch (_: Exception) {
        try {
            // Very rough fallback for "h:mm AM/PM"
            val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            LocalTime.parse(trimmed.uppercase().replace(".", ""), fmt)
        } catch (_: Exception) {
            null
        }
    }
}
