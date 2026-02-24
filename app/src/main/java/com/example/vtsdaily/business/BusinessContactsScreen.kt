package com.example.vtsdaily.business

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.vtsdaily.ui.templates.DirectoryTemplateScreen
import com.example.vtsdaily.ui.templates.SortOption

enum class SortMode { ADDRESS, NAME }

/** Content-only screen (MainActivity owns the Scaffold/topBar) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessContactsScreen(
    registerActions: ((onAdd: () -> Unit, onImportJson: () -> Unit, onExport: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var contacts by remember { mutableStateOf(BusinessContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<BusinessContact?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Tracks rows currently animating out
    val deletingKeys = remember { mutableStateOf(setOf<String>()) }

    // --- JSON IMPORT PICKER ---
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

    // --- EXPORT launcher: create a JSON document chosen by user ---
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
                            current.size
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

    LaunchedEffect(sortMode, query) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(0)
        }
    }

    val sorted = remember(contacts, sortMode) {
        when (sortMode) {
            SortMode.ADDRESS -> contacts.sortedBy { it.address.lowercase() }
            SortMode.NAME -> contacts.sortedBy { it.name.lowercase() }
        }
    }

    val filtered = remember(sorted, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) sorted
        else sorted.filter { c ->
            c.address.lowercase().contains(q) ||
                    c.name.lowercase().contains(q)
        }
    }

    val onAddClicked: () -> Unit = {
        editing = BusinessContact(name = "", address = "", phone = "")
    }
    val onImportJsonClicked: () -> Unit = {
        jsonImportPicker.launch("application/json")
    }
    val onExportClicked: () -> Unit = {
        val ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            .format(java.time.LocalDate.now())
        exportLauncher.launch("clinics-export-$ts.json")
    }

    LaunchedEffect(Unit) {
        registerActions?.invoke(onAddClicked, onImportJsonClicked, onExportClicked)
    }

    DirectoryTemplateScreen(
        items = contacts,

        sortOptions = listOf(
            SortOption(
                label = "Address",
                comparator = compareBy { it.address.lowercase() },
                primaryText = { it.address },
                secondaryText = { it.name }
            ),
            SortOption(
                label = "Name",
                comparator = compareBy { it.name.lowercase() },
                primaryText = { it.name },
                secondaryText = { it.address }
            )
        ),

        itemKey = { c -> "${c.name.lowercase()}|${c.phone}" },

        searchLabel = "Search clinics",
        searchHintPredicate = { c, q ->
            c.address.lowercase().contains(q) || c.name.lowercase().contains(q)
        },

        phoneOf = { it.phone },

        onEdit = { c -> editing = c },

        // Option A: animate out -> remove from list immediately (in-memory)
        onDeleteImmediate = { c ->
            contacts = contacts.toMutableList().also { it.remove(c) }
        },

        // Option A: only delete from store if snackbar NOT undone
        onDeleteFinal = { c ->
            BusinessContactStore.delete(appContext, c.name, c.phone)
            contacts = BusinessContactStore.load(appContext)
        },

        // Option A: undo pressed -> reload from store
        onUndo = {
            contacts = BusinessContactStore.load(appContext)
        },

        deleteSnackbarMessage = { c -> "Deleted \"${c.name}\"" }
    )

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
    sortMode: SortMode,
    onCall: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit

) {
    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {

                val primaryText =
                    if (sortMode == SortMode.NAME) contact.name else contact.address

                val secondaryText =
                    if (sortMode == SortMode.NAME) contact.address else contact.name

                if (primaryText.isNotBlank()) {
                    Text(
                        primaryText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (secondaryText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (contact.phone.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.combinedClickable(
                            onClick = { onCall(contact.phone) },
                            onLongClick = {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(ClipData.newPlainText("phone", contact.phone))
                            }
                        ),
                        maxLines = 1
                    )
                }
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