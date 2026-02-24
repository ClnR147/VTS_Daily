package com.example.vtsdaily.storage

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.ui.components.VtsSearchBar
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(context: Context) {
    var contacts by remember { mutableStateOf(ImportantContactStore.load(context)) }
    var editing by remember { mutableStateOf<ImportantContact?>(null) }
    var query by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun applyFilter(all: List<ImportantContact>, q: String): List<ImportantContact> {
        val needle = q.trim()
        return if (needle.isBlank()) all else all.filter { it.matchesQuery(needle) }
    }

    fun reload() {
        val all = ImportantContactStore.load(context)
        contacts = applyFilter(all, query)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Important Contacts", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // Import (JSON + CSV) from /sdcard/PassengerSchedules
                    IconButton(onClick = {
                        val dir = File(Environment.getExternalStorageDirectory(), "PassengerSchedules")
                        val json = File(dir, "ImportantContacts.json")
                        val csv = File(dir, "ImportantContacts.csv")

                        var added = 0
                        var merged = 0
                        var dropped = 0
                        var ranSomething = false

                        if (json.exists()) {
                            val rpt = ImportantContactStore.importFromJsonFile(context, json)
                            added += rpt.added
                            merged += rpt.merged
                            ranSomething = true
                        }
                        if (csv.exists()) {
                            val rpt = ImportantContactStore.importFromCsvFile(context, csv)
                            added += rpt.added
                            merged += rpt.merged
                            dropped += rpt.dropped
                            ranSomething = true
                        }

                        reload()

                        scope.launch {
                            if (ranSomething) {
                                val droppedPart = if (dropped > 0) ", $dropped dropped" else ""
                                snackbar.showSnackbar("Import: +$added added, $merged merged$droppedPart")
                                ImportantContactStore.backupToExternal(context)
                            } else {
                                snackbar.showSnackbar("No JSON or CSV found in PassengerSchedules/")
                            }
                        }
                    }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Import (CSV/JSON)")
                    }

                    // Restore
                    IconButton(onClick = {
                        val ok = ImportantContactStore.restoreFromExternal(context)
                        reload()
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Restored contacts from external file"
                                else "No external file to restore (or permission denied)"
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restore")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = ImportantContact(name = "", phone = "", note = "")
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // ✅ KEY FIX:
                // Horizontal padding stays, but remove the "vertical = 8.dp" top padding
                // that was pushing the SearchBar down.
                .padding(horizontal = 12.dp)
                .padding(top = 0.dp, bottom = 8.dp)
        ) {
            // Search
            VtsSearchBar(
                value = query,
                onValueChange = {
                    query = it
                    val all = ImportantContactStore.load(context)
                    contacts = applyFilter(all, it)
                },
                label = "Search by name or phone…"
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    contacts,
                    key = { "${it.name.lowercase()}|${it.phone}" }
                ) { c ->
                    ContactRow(
                        contact = c,
                        onEdit = { editing = c },
                        onDelete = {
                            val delName = c.name
                            val delPhone = c.phone

                            // Instant UI removal
                            contacts = contacts.filterNot {
                                it.name.equals(delName, ignoreCase = true) && it.phone == delPhone
                            }

                            // Persist delete + backup + reload
                            ImportantContactStore.delete(context, delName, delPhone)
                            ImportantContactStore.backupToExternal(context)
                            reload()

                            scope.launch { snackbar.showSnackbar("Deleted \"$delName\"") }
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

/* --- row --- */
@Composable
private fun ContactRow(
    contact: ImportantContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    ElevatedCard {
        Row(
            modifier = Modifier
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
                                data = "tel:${contact.phone}".toUri()
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
fun ContactDialog(
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

/** Simple client-side filter */
private fun ImportantContact.matchesQuery(q: String): Boolean {
    val needle = q.trim().lowercase()
    return name.lowercase().contains(needle) ||
            phone.lowercase().contains(needle) ||
            note.lowercase().contains(needle)
}