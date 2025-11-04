package com.example.vtsdaily.drivers

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import com.example.vtsdaily.ui.components.ScreenDividers
// + add this import at the top with the others:
import androidx.compose.foundation.lazy.itemsIndexed

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add   // ⬅️ NEW
import androidx.core.net.toUri
// + at top with other imports

private val VtsGreen = Color(0xFF4CAF50)   // green
private val VtsBannerText = Color(0xFFFFF5E1)
private val RowStripe = Color(0xFFF7F5FA)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var drivers by remember { mutableStateOf(DriverStore.load(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Driver?>(null) }
    var showAdd by remember { mutableStateOf(false) }    // ⬅️ NEW

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filtered = remember(drivers, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) drivers else drivers.filter { d ->
            d.name.lowercase().contains(q) ||
                    d.van.lowercase().contains(q) ||
                    "${d.make} ${d.model}".lowercase().contains(q)
        }
    }

    fun doImport() {
        val guesses = listOf(
            "/storage/emulated/0/PassengerSchedules/driver.xls",
            "C:/Users/kbigg/CrossDevice/KEITH's S25+ (1)/storage/PassengerSchedules/driver.xls"
        ).map(::File)

        val found = guesses.firstOrNull { it.exists() }
        if (found == null) {
            scope.launch { snackbarHostState.showSnackbar("driver.xls not found in default locations.") }
        } else {
            runCatching { DriverStore.importFromXls(found) }
                .onSuccess {
                    drivers = it
                    DriverStore.save(context, it)
                    scope.launch { snackbarHostState.showSnackbar("Imported ${it.size} drivers.") }
                }
                .onFailure { e ->
                    scope.launch { snackbarHostState.showSnackbar("Import failed: ${e.message}") }
                }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ⬅️ NEW: two FABs stacked (Add + Import)
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = VtsGreen,
                    contentColor = VtsBannerText
                ) { Icon(Icons.Filled.Add, contentDescription = "Add driver") }

                FloatingActionButton(
                    onClick = { doImport() },
                    containerColor = VtsGreen,
                    contentColor = VtsBannerText
                ) { Icon(imageVector = Icons.Filled.Upload, contentDescription = "Import XLS") }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // divider under the main app bar (match Schedule feel)
            ScreenDividers.Thick()

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search (name, van, make/model)") }
            )

            // List (extra bottom padding so rows don't hide behind the FAB)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 128.dp // ⬅️ a bit more for 2 FABs
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // stable key: name|phone
                itemsIndexed(
                    filtered,
                    key = { _, d -> "${d.name.lowercase()}|${d.phone}" }
                ) { index, d ->
                    DriverRow(
                        driver = d,
                        onClick = { selected = d },
                        onCall = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                            context.startActivity(intent)
                        },
                        onDeleteConfirmed = { toDelete ->
                            // Persist delete + refresh UI
                            drivers = DriverStore.delete(context, toDelete)

                            // Close details if it was open for this driver
                            val delKey = "${toDelete.name}|${toDelete.phone}".lowercase()
                            if (selected?.let { "${it.name}|${it.phone}".lowercase() } == delKey) {
                                selected = null
                            }

                            scope.launch { snackbarHostState.showSnackbar("Deleted \"${toDelete.name}\"") }
                        }
                    )

                    if (index < filtered.lastIndex) {
                        ScreenDividers.Thin(inset = 12.dp)
                    }
                }
            }
        }

        if (selected != null) {
            DriverDetailsDialog(
                driver = selected!!,
                onDismiss = { selected = null },
                onCall = { phone ->
                    val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                    context.startActivity(intent)
                }
            )
        }

        // ⬅️ NEW: Add dialog
        if (showAdd) {
            AddDriverDialog(
                onDismiss = { showAdd = false },
                onAdd = { newDriver ->
                    val updated = drivers.toMutableList().apply { add(newDriver) }
                    drivers = updated
                    DriverStore.save(context, updated)
                    scope.launch { snackbarHostState.showSnackbar("Added \"${newDriver.name}\"") }
                    showAdd = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DriverRow(
    driver: Driver,
    onClick: () -> Unit,
    onCall: (String) -> Unit,
    onDeleteConfirmed: (Driver) -> Unit   // ⬅️ delete hook
) {
    var showConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onCall(driver.phone) }
            )
    ) {
        Row(
            modifier = Modifier
                .background(RowStripe)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Van pill
            Box(
                modifier = Modifier
                    .background(VtsGreen, shape = MaterialTheme.shapes.large)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(driver.van, color = VtsBannerText, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driver.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = buildString {
                    if (driver.year != null) append("${driver.year} ")
                    append(driver.make)
                    if (driver.model.isNotBlank()) append(" ${driver.model}")
                }.trim()
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF56536A),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Phone (tap to call)
            Text(
                text = driver.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onCall(driver.phone) },
                maxLines = 1, overflow = TextOverflow.Clip
            )

            // Delete icon
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete driver"
                )
            }
        }
    }

    // Confirm dialog
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete driver?") },
            text = { Text("Remove ${driver.name} from your drivers list?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onDeleteConfirmed(driver)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DriverDetailsDialog(
    driver: Driver,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color = VtsGreen, shape = MaterialTheme.shapes.large)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(driver.van, color = VtsBannerText, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append("• ")
                        if (driver.year != null) append("${driver.year} ")
                        append(driver.make)
                        if (driver.model.isNotBlank()) append(" ${driver.model}")
                    }.trim(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column {
                Text(driver.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Phone: ", fontWeight = FontWeight.SemiBold)
                    Text(
                        driver.phone,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onCall(driver.phone) }
                    )
                }
            }
        }
    )
}

/** ⬅️ NEW: Add Driver dialog */
@Composable
private fun AddDriverDialog(
    onDismiss: () -> Unit,
    onAdd: (Driver) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var van by rememberSaveable { mutableStateOf("") }
    var yearText by rememberSaveable { mutableStateOf("") }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }

    val isValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Driver") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(value = van, onValueChange = { van = it }, label = { Text("Van") }, singleLine = true)
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Year") },
                    singleLine = true
                )
                OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val year = yearText.toIntOrNull()
                    onAdd(
                        Driver(
                            name = name.trim(),
                            phone = phone.trim(),
                            van = van.trim(),
                            year = year,
                            make = make.trim(),
                            model = model.trim()
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
