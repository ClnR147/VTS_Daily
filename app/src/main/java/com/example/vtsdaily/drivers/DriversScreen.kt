package com.example.vtsdaily.drivers

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.io.File
import com.example.vtsdaily.ui.components.VtsCard
import com.example.vtsdaily.ui.components.VtsSearchBar

private val VtsGreen = Color(0xFF4CAF50)   // green
private val VtsBannerText = Color(0xFFFFF5E1)
val RowStripe = Color(0xFFF7F5FA)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen(
    // Register actions so MainActivity's global top bar can invoke these
    registerActions: (onAdd: () -> Unit, onImport: () -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var drivers by remember { mutableStateOf(DriverStore.load(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Driver?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Driver?>(null) }

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

    // Expose Add / Import handlers to MainActivity's DriversTopBarCustom
    LaunchedEffect(Unit) {
        registerActions(
            { showAdd = true },   // onAdd
            { doImport() }        // onImport
        )
    }

    // Content-only scaffold: no topBar here
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            VtsSearchBar(
                value = query,
                onValueChange = { query = it },
                label = "Search (name, van, make/model)"
            )

            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            drivers = DriverStore.delete(context, toDelete)

                            val delKey = "${toDelete.name}|${toDelete.phone}".lowercase()
                            if (selected?.let { "${it.name}|${it.phone}".lowercase() } == delKey) {
                                selected = null
                            }

                            scope.launch { snackbarHostState.showSnackbar("Deleted \"${toDelete.name}\"") }
                        }
                    )

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
                },
                onEdit = { d ->
                    selected = null
                    editing = d
                }
            )
        }

        if (editing != null) {
            val editingDriver = editing!!
            EditDriverDialog(
                initial = editingDriver,
                onDismiss = { editing = null },
                onSave = { updatedDriver ->
                    val updatedList = DriverStore.update(
                        context = context,
                        original = editingDriver,   // original key (pre-edit)
                        updated = updatedDriver     // new values
                    )
                    drivers = updatedList
                    editing = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Updated \"${updatedDriver.name}\"")
                    }
                }
            )
        }

        if (showAdd) {
            AddDriverDialog(
                onDismiss = { showAdd = false },
                onAdd = { newDriver ->
                    val updated = DriverStore.add(context, newDriver)
                    drivers = updated
                    scope.launch {
                        snackbarHostState.showSnackbar("Added \"${newDriver.name}\"")
                    }
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
    onDeleteConfirmed: (Driver) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    VtsCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onCall(driver.phone) }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                maxLines = 1,
                overflow = TextOverflow.Clip
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
    onCall: (String) -> Unit,
    onEdit: (Driver) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onEdit(driver) }) {
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color = VtsGreen, shape = MaterialTheme.shapes.large)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        driver.van,
                        color = VtsBannerText,
                        fontWeight = FontWeight.SemiBold
                    )
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

@Composable
private fun EditDriverDialog(
    initial: Driver,
    onDismiss: () -> Unit,
    onSave: (Driver) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var van by rememberSaveable { mutableStateOf(initial.van) }
    var yearText by rememberSaveable { mutableStateOf(initial.year?.toString().orEmpty()) }
    var make by rememberSaveable { mutableStateOf(initial.make) }
    var model by rememberSaveable { mutableStateOf(initial.model) }

    val isValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Driver") },
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
                    onSave(
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Add Driver dialog */
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
