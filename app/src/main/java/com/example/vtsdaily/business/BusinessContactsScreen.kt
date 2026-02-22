package com.example.vtsdaily.business

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.ui.components.ScreenDividers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    c.name.lowercase().contains(q) ||
                    c.phone.lowercase().contains(q)
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(6.dp))
            ScreenDividers.Thick()

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                SegmentedButton(
                    selected = sortMode == SortMode.ADDRESS,
                    onClick = { sortMode = SortMode.ADDRESS },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Address") }

                SegmentedButton(
                    selected = sortMode == SortMode.NAME,
                    onClick = { sortMode = SortMode.NAME },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Name") }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search clinics") },
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
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
                    top = 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    filtered,
                    key = { _, c -> "${c.name.lowercase()}|${c.phone}" }
                ) { _, c ->
                    val itemKey = "${c.name.lowercase()}|${c.phone}"
                    val isDeleting = deletingKeys.value.contains(itemKey)

                    AnimatedVisibility(
                        visible = !isDeleting,
                        enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
                    ) {
                        BusinessContactRow(
                            contact = c,
                            onCall = { phone ->
                                val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                                context.startActivity(intent)
                            },
                            onEdit = { editing = c },
                            onDelete = {
                                scope.launch {
                                    deletingKeys.value = deletingKeys.value + itemKey
                                    delay(180)

                                    val updatedList = contacts.toMutableList()
                                    updatedList.remove(c)
                                    contacts = updatedList

                                    deletingKeys.value = deletingKeys.value - itemKey

                                    val result = snackbar.showSnackbar(
                                        message = "Deleted \"${c.name}\"",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Long
                                    )

                                    if (result == SnackbarResult.ActionPerformed) {
                                        contacts = BusinessContactStore.load(appContext)
                                    } else {
                                        BusinessContactStore.delete(appContext, c.name, c.phone)
                                        contacts = BusinessContactStore.load(appContext)
                                    }
                                }
                            }
                        )
                    }
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
                if (contact.address.isNotBlank()) {
                    Text(
                        contact.address,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (contact.name.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        contact.name,
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