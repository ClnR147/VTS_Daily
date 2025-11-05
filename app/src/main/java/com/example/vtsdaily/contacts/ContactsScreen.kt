package com.example.vtsdaily.contacts

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.ui.components.ScreenDividers
import com.example.vtsdaily.storage.ImportantContact
import com.example.vtsdaily.storage.ImportantContactStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val RowStripe = Color(0xFFF7F5FA)

/** Call this from MainActivity when view == 3 */
@Composable
fun ContactsScreen() {
    val context = LocalContext.current          // for startActivity
    val appContext = context.applicationContext // for file I/O

    var contacts by remember { mutableStateOf(ImportantContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<ImportantContact?>(null) }
    var actionsOpen by remember { mutableStateOf(false) } // ⋮ menu
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Pickers
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
    val jsonPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val tmp = copyUriToCache(appContext, uri, "contacts_import.json")
                    val report = withContext(Dispatchers.IO) {
                        ImportantContactStore.importFromJsonFile(appContext, tmp)
                    }
                    contacts = ImportantContactStore.load(appContext)
                    snackbar.showSnackbar(
                        "JSON import: kept=${report.kept}, merged=${report.merged}, added=${report.added}, dropped=${report.dropped}" +
                                if (report.errors.isNotEmpty()) " (${report.errors.first()})" else ""
                    )
                }
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
        // NOTE: no FAB — Add is inside the ⋮ menu now
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {

            // Main content (no extra header rows → divider stays aligned)
            Column(Modifier.fillMaxSize()) {
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
                                ImportantContactStore.delete(appContext, c.name, c.phone)
                                contacts = ImportantContactStore.load(appContext)
                                scope.launch { snackbar.showSnackbar("Deleted \"${c.name}\"") }
                            }
                        )
                        if (index < contacts.lastIndex) ScreenDividers.Thin(inset = 12.dp)
                    }
                }
            }

            // OVERLAYED ⋮ actions in the top-right (no vertical space taken)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            ) {
                IconButton(onClick = { actionsOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Contacts Actions")
                }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add Contact") },
                        onClick = { actionsOpen = false; editing = ImportantContact("", "") }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Backup to External") },
                        onClick = {
                            actionsOpen = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    ImportantContactStore.backupToExternal(appContext)
                                }
                                snackbar.showSnackbar(if (ok) "Backup complete" else "Backup failed")
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Restore from External") },
                        onClick = {
                            actionsOpen = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    ImportantContactStore.restoreFromExternal(appContext)
                                }
                                contacts = ImportantContactStore.load(appContext)
                                snackbar.showSnackbar(if (ok) "Restore complete" else "No backup found")
                            }
                        }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Import CSV…") },
                        onClick = { actionsOpen = false; csvPicker.launch("*/*") }
                    )
                    DropdownMenuItem(
                        text = { Text("Import JSON…") },
                        onClick = { actionsOpen = false; jsonPicker.launch("application/json") }
                    )
                }
            }
        }
    }

    editing?.let {
        AddEditContactDialog(
            initial = it,
            onDismiss = { editing = null },
            onSave = { updated ->
                ImportantContactStore.upsert(appContext, updated)
                contacts = ImportantContactStore.load(appContext)
                editing = null
            }
        )
    }
}

/** Row uses an action cluster: Call • Edit • Delete (no per-row epsilon) */


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
                // Phone is the call target (tap) + copy (long-press)
                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onCall(contact.phone) },
                            onLongClick = {
                                clipboard.setText(AnnotatedString(contact.phone))
                            }
                        ),
                    maxLines = 1
                )
            }

            // Actions: Edit • Delete (no phone icon)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}


/** Minimal in-file dialog for adding/editing a contact */
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name*") },
                    singleLine = true
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
