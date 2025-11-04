package com.example.vtsdaily.storage

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(context: Context) {
    // Safe load (never throws)
    var contacts by remember { mutableStateOf(ImportantContactStore.load(context)) }
    var editing by remember { mutableStateOf<ImportantContact?>(null) }
    var query by remember { mutableStateOf(TextFieldValue("")) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun reload() {
        contacts = ImportantContactStore.load(context)
        // re-apply search filter after reload
        val q = query.text
        if (q.isNotBlank()) {
            contacts = contacts.filter { it.matchesQuery(q) }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Important Contacts", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // Import CSV from /sdcard/PassengerSchedules/ImportantContacts.csv
                    IconButton(onClick = {
                        val csv = File(
                            Environment.getExternalStorageDirectory(),
                            "PassengerSchedules/ImportantContacts.csv"
                        )
                        val rpt = ImportantContactStore.importFromCsvFile(context, csv)
                        reload()
                        scope.launch {
                            snackbar.showSnackbar(
                                "CSV import: +${rpt.added} added, ${rpt.merged} merged, ${rpt.dropped} dropped"
                            )
                        }
                    }) { Icon(Icons.Filled.FileUpload, contentDescription = "Import CSV") }

                    // Import JSON from /sdcard/PassengerSchedules/ImportantContacts.json
                    IconButton(onClick = {
                        val json = File(
                            Environment.getExternalStorageDirectory(),
                            "PassengerSchedules/ImportantContacts.json"
                        )
                        val rpt = ImportantContactStore.importFromJsonFile(context, json)
                        reload()
                        scope.launch {
                            snackbar.showSnackbar("JSON import: +${rpt.added} added, ${rpt.merged} merged")
                        }
                    }) { Icon(Icons.Filled.FileUpload, contentDescription = "Import JSON") }

                    IconButton(onClick = {
                        val ok = ImportantContactStore.backupToExternal(context)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Backed up to app external: PassengerSchedules/ImportantContacts.json"
                                else "Backup failed (permission/path?)"
                            )
                        }
                    }) { Icon(Icons.Filled.Backup, contentDescription = "Backup") }

                    IconButton(onClick = {
                        val ok = ImportantContactStore.restoreFromExternal(context)
                        reload()
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Restored contacts from external file"
                                else "No external file to restore (or permission denied)"
                            )
                        }
                    }) { Icon(Icons.Filled.Restore, contentDescription = "Restore") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ImportantContact(name = "", phone = "", note = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    contacts = if (it.text.isBlank()) {
                        ImportantContactStore.load(context)
                    } else {
                        ImportantContactStore.load(context).filter { c -> c.matchesQuery(it.text) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by name or phone…") },
                singleLine = true
            )

            Divider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    contacts,
                    key = { "${it.name.lowercase()}|${it.phone}" } // safer key than name-only
                ) { c ->
                    ContactRow(
                        contact = c,
                        onEdit = { editing = c },
                        onDelete = {
                            ImportantContactStore.delete(context, c.name)
                            reload()
                            scope.launch { snackbar.showSnackbar("Deleted \"${c.name}\"") }
                        }
                    )
                }
            }
        }

        if (editing != null) {
            ContactDialog(
                initial = editing!!,
                onDismiss = { editing = null },
                onSave = { updated ->
                    ImportantContactStore.upsert(context, updated)
                    reload()
                    editing = null
                    scope.launch { snackbar.showSnackbar("Saved \"${updated.name}\"") }
                }
            )
        }
    }
}

/* --- small helpers --- */
@Composable
private fun ContactRow(
    contact: ImportantContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    ElevatedCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)

                if (contact.phone.isNotBlank()) {
                    Text(
                        text = contact.phone,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${contact.phone}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                if (contact.note.isNotBlank()) {
                    Text(contact.note, style = MaterialTheme.typography.bodySmall)
                }
            }

            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ContactDialog(
    initial: ImportantContact,
    onDismiss: () -> Unit,
    onSave: (ImportantContact) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial.name)) }
    var phone by remember { mutableStateOf(TextFieldValue(initial.phone)) }
    var note by remember { mutableStateOf(TextFieldValue(initial.note)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Add Contact" else "Edit Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ImportantContact(
                        name = name.text.trim(),
                        phone = phone.text.trim(),
                        note = note.text.trim()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Simple client-side filter to avoid a Store dependency for search */
private fun ImportantContact.matchesQuery(q: String): Boolean {
    val needle = q.trim().lowercase()
    return name.lowercase().contains(needle) ||
            phone.lowercase().contains(needle) ||
            note.lowercase().contains(needle)
}
