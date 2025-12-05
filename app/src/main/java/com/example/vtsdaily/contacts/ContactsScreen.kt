package com.example.vtsdaily.contacts

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.storage.ImportantContact
import com.example.vtsdaily.storage.ImportantContactStore
import com.example.vtsdaily.ui.components.ScreenDividers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


val RowStripe = Color(0xFFF7F5FA)

/** Content-only screen (MainActivity owns the Scaffold/topBar) */
@Composable
fun ContactsScreen(
    registerActions: ((
        onAdd: () -> Unit,
        onBackup: () -> Unit,
        onRestore: () -> Unit,
        onImportCsv: () -> Unit,
        onImportJson: () -> Unit
    ) -> Unit)? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var contacts by remember { mutableStateOf(ImportantContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<ImportantContact?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- IMPORT PICKERS (CSV + JSON merge/import) ---

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val tmp = copyUriToCache(appContext, uri, "contacts_import.csv")
                    val report = withContext(Dispatchers.IO) {
                        ImportantContactStore.importFromCsvFile(appContext, tmp)
                    }
                    contacts = ImportantContactStore.load(appContext)
                    snackbar.showSnackbar(
                        "CSV import: kept=${report.kept}, merged=${report.merged}, added=${report.added}, dropped=${report.dropped}" +
                                if (report.errors.isNotEmpty()) " (${report.errors.first()})" else ""
                    )
                }
            }
        }
    )

    val jsonImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            // Read entire file as text
                            val text = appContext.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                ?: throw IllegalArgumentException("Empty or unreadable file")

                            // Expect an array of objects, each with name/phone
                            val array = org.json.JSONArray(text)

                            // Load current contacts once
                            val current = ImportantContactStore.load(appContext).toMutableList()

                            var added = 0
                            var updated = 0

                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)

                                // Try several key spellings, just in case
                                val rawName = when {
                                    obj.has("name") && !obj.isNull("name") -> obj.get("name")
                                    obj.has("Name") && !obj.isNull("Name") -> obj.get("Name")
                                    else -> ""
                                }.toString()

                                val rawPhone = when {
                                    obj.has("phone") && !obj.isNull("phone") -> obj.get("phone")
                                    obj.has("Phone") && !obj.isNull("Phone") -> obj.get("Phone")
                                    else -> ""
                                }.toString()

                                val name = rawName.trim()
                                val phone = rawPhone.trim()

                                // Skip entries with no name
                                if (name.isBlank()) continue

                                val contact = ImportantContact(name = name, phone = phone)

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

                                // Persist via store
                                ImportantContactStore.upsert(appContext, contact)
                            }

                            // Return stats
                            added to updated
                        }
                    }

                    if (result.isSuccess) {
                        val (added, updated) = result.getOrDefault(0 to 0)
                        contacts = ImportantContactStore.load(appContext)
                        snackbar.showSnackbar("JSON import: added=$added, updated=$updated")
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unsupported JSON format"
                        snackbar.showSnackbar("JSON import failed: $msg")
                    }
                }
            }
        }
    )



    // --- BACKUP (save all contacts to external JSON) ---

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val jsonArray = org.json.JSONArray()
                            contacts.forEach { c ->
                                val obj = org.json.JSONObject()
                                obj.put("name", c.name)
                                obj.put("phone", c.phone)
                                jsonArray.put(obj)
                            }

                            appContext.contentResolver.openOutputStream(uri).use { out ->
                                requireNotNull(out) { "Unable to open backup file for writing." }
                                out.write(jsonArray.toString(2).toByteArray())
                            }

                            contacts.size // return count for snackbar
                        }
                    }

                    snackbar.showSnackbar(
                        if (result.isSuccess) {
                            "Contacts backup saved (${result.getOrNull()} contacts)"
                        } else {
                            "Backup failed: ${
                                result.exceptionOrNull()?.message ?: "unknown error"
                            }"
                        }
                    )
                }
            }
        }
    )

    // --- RESTORE (load backup JSON and replace the list) ---

    val restoreLauncher = rememberLauncherForActivityResult(
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

                            val jsonArray = org.json.JSONArray(text)
                            val restored = mutableListOf<ImportantContact>()

                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name", "").trim()
                                val phone = obj.optString("phone", "").trim()
                                if (name.isNotEmpty()) {
                                    restored.add(ImportantContact(name = name, phone = phone))
                                }
                            }

                            // Replace existing contacts with restored set
                            val current = ImportantContactStore.load(appContext)

                            // Delete any current contact NOT in restored
                            current.forEach { cur ->
                                if (restored.none {
                                        it.name.equals(cur.name, ignoreCase = true) &&
                                                it.phone == cur.phone
                                    }
                                ) {
                                    ImportantContactStore.delete(appContext, cur.name, cur.phone)
                                }
                            }

                            // Upsert all restored contacts
                            restored.forEach { ImportantContactStore.upsert(appContext, it) }

                            restored.size
                        }
                    }

                    contacts = ImportantContactStore.load(appContext)

                    snackbar.showSnackbar(
                        if (result.isSuccess) {
                            "Restore complete (${result.getOrNull()} contacts)"
                        } else {
                            "Restore failed: ${
                                result.exceptionOrNull()?.message ?: "unknown error"
                            }"
                        }
                    )
                }
            }
        }
    )

    // --- Actions exposed to MainActivity / top bar ---

    val onAddClicked: () -> Unit = {
        editing = ImportantContact(name = "", phone = "")
    }

    val onBackupClicked: () -> Unit = {
        backupLauncher.launch("contacts-backup.json")
    }

    val onRestoreClicked: () -> Unit = {
        restoreLauncher.launch("application/json")
    }

    val onImportCsvClicked: () -> Unit = {
        csvPicker.launch("text/*")
    }

    val onImportJsonClicked: () -> Unit = {
        jsonImportPicker.launch("application/json")
    }

    LaunchedEffect(Unit) {
        registerActions?.invoke(
            onAddClicked,
            onBackupClicked,
            onRestoreClicked,
            onImportCsvClicked,
            onImportJsonClicked
        )
    }

    // --- BODY (unchanged) ---

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(6.dp))
            ScreenDividers.Thick()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    contacts,
                    key = { _, c -> "${c.name.lowercase()}|${c.phone}" }
                ) { index, c ->
                    ContactRow(
                        contact = c,
                        onCall = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                            context.startActivity(intent)
                        },
                        onEdit = { editing = c },
                        onDelete = {
                            scope.launch {
                                val ok = runCatching {
                                    ImportantContactStore.delete(appContext, c.name, c.phone)
                                    contacts = ImportantContactStore.load(appContext)
                                }.isSuccess
                                snackbar.showSnackbar(if (ok) "Deleted \"${c.name}\"" else "Delete failed")
                            }
                        }
                    )
                    if (index < contacts.lastIndex) ScreenDividers.Thin(inset = 12.dp)
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
        AddEditContactDialog(
            initial = it,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    runCatching {
                        ImportantContactStore.upsert(appContext, updated)
                        contacts = ImportantContactStore.load(appContext)
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
private fun ContactRow(
    contact: ImportantContact,
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
                .background(RowStripe)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.combinedClickable(
                        onClick = { onCall(contact.phone) },
                        onLongClick = { clipboard.setText(AnnotatedString(contact.phone)) }
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
private fun AddEditContactDialog(
    initial: ImportantContact,
    onDismiss: () -> Unit,
    onSave: (ImportantContact) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var phone by remember(initial) { mutableStateOf(initial.phone) }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Add Contact" else "Edit Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                onSave(ImportantContact(name = name.trim(), phone = phone.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* Helper: copy selected file to cache so we can read it */
private suspend fun copyUriToCache(appContext: android.content.Context, uri: Uri, name: String): File {
    return withContext(Dispatchers.IO) {
        val tmp = File(appContext.cacheDir, name)
        appContext.contentResolver.openInputStream(uri).use { input ->
            tmp.outputStream().use { out ->
                if (input != null) input.copyTo(out) else out.write(ByteArray(0))
            }
        }
        tmp
    }
}
