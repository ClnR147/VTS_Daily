package com.example.vtsdaily.business

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.ui.components.ScreenDividers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState

val BusinessRowStripe = androidx.compose.ui.graphics.Color(0xFFF7F5FA)
enum class SortMode { ADDRESS, NAME }

/** Content-only screen (MainActivity owns the Scaffold/topBar) */
// Change signature to include export callback
@Composable
fun BusinessContactsScreen(
    registerActions: ((onAdd: () -> Unit, onImportJson: () -> Unit, onExport: () -> Unit) -> Unit)? = null
) {
    // existing state...
    val context = LocalContext.current
    val appContext = context.applicationContext
    var contacts by remember { mutableStateOf(BusinessContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<BusinessContact?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- JSON IMPORT PICKER (existing) ---
    // ... your existing jsonImportPicker here ...
    val jsonImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val text = appContext.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                ?: "[]"

                            val array = org.json.JSONArray(text)

                            // Load current once
                            val current = BusinessContactStore.load(appContext).toMutableList()

                            var added = 0
                            var updated = 0

                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)

                                val name = when {
                                    obj.has("name") && !obj.isNull("name") -> obj.get("name")
                                    obj.has("Name") && !obj.isNull("Name") -> obj.get("Name")
                                    else -> ""
                                }.toString().trim()

                                val address = when {
                                    obj.has("address") && !obj.isNull("address") -> obj.get("address")
                                    obj.has("Address") && !obj.isNull("Address") -> obj.get("Address")
                                    else -> ""
                                }.toString().trim()

                                val phone = when {
                                    obj.has("phone") && !obj.isNull("phone") -> obj.get("phone")
                                    obj.has("Phone") && !obj.isNull("Phone") -> obj.get("Phone")
                                    else -> ""
                                }.toString().trim()

                                if (name.isBlank() && address.isBlank() && phone.isBlank()) continue

                                // require at least address OR name (your call; address is primary)
                                if (address.isBlank() && name.isBlank()) continue

                                val contact = BusinessContact(
                                    name = name,
                                    address = address,
                                    phone = phone
                                )

                                val existingIndex = current.indexOfFirst {
                                    it.name.equals(name, ignoreCase = true) && it.phone == phone
                                }

                                if (existingIndex >= 0) {
                                    current[existingIndex] = contact
                                    updated++
                                } else {
                                    current.add(contact)
                                    added++
                                }

                                BusinessContactStore.upsert(appContext, contact)
                            }

                            added to updated
                        }
                    }

                    if (result.isSuccess) {
                        val (added, updated) = result.getOrDefault(0 to 0)
                        contacts = BusinessContactStore.load(appContext)
                        snackbar.showSnackbar("JSON import: added=$added, updated=$updated")
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unsupported JSON format"
                        snackbar.showSnackbar("JSON import failed: $msg")
                    }
                }
            }
        }
    )
    // --- EXPORT (backup) launcher: create a JSON document chosen by user ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val arr = org.json.JSONArray()
                            val current = BusinessContactStore.load(appContext)
                            current.forEach { c ->
                                val o = org.json.JSONObject()
                                o.put("name", c.name)
                                o.put("address", c.address)
                                o.put("phone", c.phone)
                                arr.put(o)
                            }
                            appContext.contentResolver.openOutputStream(uri).use { out ->
                                requireNotNull(out) { "Unable to open export file for writing." }
                                out.write(arr.toString(2).toByteArray())
                            }
                            current.size // return count
                        }
                    }

                    snackbar.showSnackbar(
                        if (result.isSuccess) {
                            "Export saved (${result.getOrNull()} contacts)"
                        } else {
                            "Export failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                        }
                    )
                }
            }
        }
    )

    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.ADDRESS) }

    val listState = rememberLazyListState()
    LaunchedEffect(sortMode) {
        listState.scrollToItem(0)
    }
    val sorted = remember(contacts, sortMode) {
        when (sortMode) {
            SortMode.ADDRESS ->
                contacts.sortedBy { it.address.lowercase() }
            SortMode.NAME ->
                contacts.sortedBy { it.name.lowercase() }
        }
    }

    val filtered = remember(sorted, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) sorted
        else sorted.filter { c ->
            c.address.lowercase().contains(q) ||
                    c.name.lowercase().contains(q) ||
                    c.phone.lowercase().contains(q)
        }
    }

    // onAdd + onImportJson (existing)
    val onAddClicked: () -> Unit = {
        editing = BusinessContact(name = "", address = "", phone = "")
    }
    val onImportJsonClicked: () -> Unit = {
        jsonImportPicker.launch("application/json")
    }

    // Export caller to be registered with MainActivity top bar
    val onExportClicked: () -> Unit = {
        // build a suggested filename with date
        val ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now())
        exportLauncher.launch("clinics-export-$ts.json")
    }

    LaunchedEffect(Unit) {
        // register all three actions
        registerActions?.invoke(onAddClicked, onImportJsonClicked, onExportClicked)
    }


    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(6.dp))
            ScreenDividers.Thick()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort:",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 4.dp)
                )

                Text(
                    text = if (sortMode == SortMode.ADDRESS) "Address" else "Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            sortMode = if (sortMode == SortMode.ADDRESS)
                                SortMode.NAME
                            else
                                SortMode.ADDRESS
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search address / name / phone") },
                singleLine = true,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    filtered,
                    key = { _, c -> "${c.name.lowercase()}|${c.phone}" }
                ) { index, c ->
                    BusinessContactRow(
                        contact = c,
                        onCall = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                            context.startActivity(intent)
                        },
                        onEdit = { editing = c },
                        onDelete = {
                            scope.launch {
                                val ok = runCatching {
                                    BusinessContactStore.delete(appContext, c.name, c.phone)
                                    contacts = BusinessContactStore.load(appContext)
                                }.isSuccess
                                snackbar.showSnackbar(if (ok) "Deleted \"${c.name}\"" else "Delete failed")
                            }
                        }
                    )
                    if (index < filtered.lastIndex) ScreenDividers.Thin(inset = 12.dp)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    editing?.let {
        AddEditBusinessContactDialog(
            initial = it,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    runCatching {
                        BusinessContactStore.upsert(appContext, updated)
                        contacts = BusinessContactStore.load(appContext)
                    }.onFailure { t ->
                        snackbar.showSnackbar("Save failed: ${t.message ?: t::class.simpleName}")
                    }
                    editing = null
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BusinessContactRow(
    contact: BusinessContact,
    onCall: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(BusinessRowStripe)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {

                // PRIMARY: Address (what you drive by)
                if (contact.address.isNotBlank()) {
                    Text(
                        contact.address,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // SECONDARY: Clinic name / description
                if (contact.name.isNotBlank()) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Phone (tap to call, long-press to copy)
                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.combinedClickable(
                        onClick = { if (contact.phone.isNotBlank()) onCall(contact.phone) },
                        onLongClick = {
                            if (contact.phone.isNotBlank())
                                clipboard.setText(AnnotatedString(contact.phone))
                        }
                    ),
                    maxLines = 1
                )
            }


            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@Composable
private fun AddEditBusinessContactDialog(
    initial: BusinessContact,
    onDismiss: () -> Unit,
    onSave: (BusinessContact) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var address by remember(initial) { mutableStateOf(initial.address) }
    var phone by remember(initial) { mutableStateOf(initial.phone) }

    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Add Business Contact" else "Edit Business Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name*") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") }
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        BusinessContact(
                            name = name.trim(),
                            address = address.trim(),
                            phone = phone.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
