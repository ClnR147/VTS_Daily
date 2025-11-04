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
import java.time.DateTimeException
import java.time.LocalDate
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

class DateSelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // If you have an app theme like VtsDailyTheme, wrap with it here.
            // VtsDailyTheme {
            MaterialTheme {
                ProvideDividerStyles {
                    DateSelectScreen()
                }
            }
            // }
        }
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

    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var trips by remember { mutableStateOf<List<LookupRow>>(emptyList()) }
    var dateDigits by rememberSaveable { mutableStateOf("") } // RAW DIGITS ONLY

    val onLoadDigits: (String) -> Unit = remember(context, snackbar, scope) {
        { digits ->
            val parsed = parseDateDigits(digits)
            if (parsed == null) {
                scope.launch { snackbar.showSnackbar("Enter date as MMDDYY or MMDDYYYY") }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { if (dateDigits.isNotBlank()) onLoadDigits(dateDigits) }) {
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
                    }) { Text("Load") }
                }

                if (selectedDate != null) {
                    Text(
                        text = "Selected: ${fmtDate(selectedDate!!)}  •  Trips: ${trips.size}",
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
                    itemsIndexed(trips) { index, r ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(r.passenger.orEmpty(), style = MaterialTheme.typography.titleMedium)

                                val puAddr = r.pAddress.orEmpty()
                                val doAddr = r.dAddress.orEmpty()
                                if (puAddr.isNotBlank()) {
                                    Text("Pickup: $puAddr", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (doAddr.isNotBlank()) {
                                    Text("Drop-off: $doAddr", style = MaterialTheme.typography.bodyMedium)
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

/* ---------------------- DATE PARSING ---------------------- */
private fun parseUserDateInput(raw: String): LocalDate? {
    val s = raw
        .replace('\u00A0', ' ')
        .replace('\u2007', ' ')
        .replace('\u202F', ' ')
        .trim()
        .replace(Regex("\\s+"), "")
    val m = Regex("""^(\d{1,2})[\/-](\d{1,2})[\/-](\d{2}|\d{4})$""").matchEntire(s) ?: return null
    val (mStr, dStr, yStr) = m.destructured
    val month = mStr.toIntOrNull() ?: return null
    val day = dStr.toIntOrNull() ?: return null
    var year = yStr.toIntOrNull() ?: return null
    if (year < 100) year += 2000

    return try { LocalDate.of(year, month, day) } catch (_: DateTimeException) { null }
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
