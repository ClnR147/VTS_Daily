package com.example.vtsdaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import java.time.DateTimeException
import java.time.LocalDate
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController



class DateSelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DateSelectScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectScreen() {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current



    var dateInput by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var trips by remember { mutableStateOf<List<LookupRow>>(emptyList()) }

    // Use a lambda instead of a local function to avoid the composable invocation error.
    val onLoad: (String) -> Unit = remember(context, snackbar, scope) {
        { input ->
            val parsed = parseUserDateInput(input)
            if (parsed == null) {
                scope.launch { snackbar.showSnackbar("Enter a valid date like 6/7/25 or 6/7/2025") }
            } else {
                selectedDate = parsed
                val sorted = LookupStore.tripsOnChrono(LookupStore.load(context), parsed)
                trips = sorted
                scope.launch { snackbar.showSnackbar("Loaded ${sorted.size} trips for ${fmtDate(parsed)}") }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trips by Date") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { if (dateInput.isNotBlank()) onLoad(dateInput) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Date (M/D/YY or M/D/YYYY)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus(force = true) // ensure field loses focus
                            keyboardController?.hide()            // hide IME
                            onLoad(dateInput)
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    focusManager.clearFocus(force = true)     // ensure field loses focus
                    keyboardController?.hide()               // hide IME
                    onLoad(dateInput)
                }) { Text("Load") }
            }

            if (selectedDate != null) {
                Text(
                    text = "Selected: ${fmtDate(selectedDate!!)}  •  Trips: ${trips.size}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Divider()

            // Simple list of passengers for that date
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(trips) { r ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.passenger.orEmpty(), style = MaterialTheme.typography.titleMedium)

                            val pu = r.pAddress.orEmpty()
                            val dr = r.dAddress.orEmpty()
                            if (pu.isNotBlank()) Text("Pickup: $pu", style = MaterialTheme.typography.bodyMedium)
                            if (dr.isNotBlank()) Text("Drop-off: $dr", style = MaterialTheme.typography.bodyMedium)

                            val tt  = r.tripType?.ifBlank { if (!r.rtTime.isNullOrBlank()) "Return" else "Appt" } ?: ""
                            val puT = r.puTimeAppt.orEmpty()
                            val doT = r.doTimeAppt.orEmpty()
                            val rtT = r.rtTime.orEmpty()

                            // Build: TripType • PU → DO • RT
                            val puDo = listOfNotNull(
                                puT.takeIf { it.isNotBlank() }?.let { "PU $it" },
                                doT.takeIf { it.isNotBlank() }?.let { "DO $it" }
                            ).joinToString(" \u2192 ") // → arrow

                            val line = listOfNotNull(
                                tt.ifBlank { null },
                                puDo.ifBlank { null },
                                rtT.takeIf { it.isNotBlank() }?.let { "RT $it" }
                            ).joinToString(" • ")

                            Text(line)
                        }
                    }
                }

            }
        }
    }
}

/* ---------------------- DATE PARSING ---------------------- */
/**
 * Accepts:
 *  - M/D/YY   (e.g., 6/7/25 → 2025-06-07)
 *  - M/D/YYYY (e.g., 6/7/2025)
 * Separators may be '/' or '-'. Leading zeros not required.
 * Two-digit years are treated as 2000..2099.
 */
private fun parseUserDateInput(raw: String): LocalDate? {
    val s = raw
        .replace('\u00A0', ' ') // NBSP
        .replace('\u2007', ' ')
        .replace('\u202F', ' ')
        .trim()
        .replace(Regex("\\s+"), "") // remove inner spaces
    val m = Regex("""^(\d{1,2})[\/-](\d{1,2})[\/-](\d{2}|\d{4})$""").matchEntire(s) ?: return null
    val (mStr, dStr, yStr) = m.destructured
    val month = mStr.toIntOrNull() ?: return null
    val day = dStr.toIntOrNull() ?: return null
    var year = yStr.toIntOrNull() ?: return null
    if (year < 100) year += 2000 // interpret 2-digit as 2000–2099

    return try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }
}

private fun fmtDate(d: LocalDate): String =
    "${d.monthValue}/${d.dayOfMonth}/${d.year}"
