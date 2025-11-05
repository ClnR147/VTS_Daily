package com.example.vtsdaily.contacts

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
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
import kotlinx.coroutines.launch

private val VtsGreen = Color(0xFF4CAF50)
private val RowStripe = Color(0xFFF7F5FA)

/** Call this from MainActivity when view == 3 */
@Composable
fun ContactsScreen() {
    val context = LocalContext.current          // ← capture Activity context for startActivity
    val appContext = context.applicationContext // ← use for file I/O in the store

    var contacts by remember { mutableStateOf(ImportantContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<ImportantContact?>(null) }
    var menuOpenFor by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = ImportantContact(name = "", phone = "") },
                containerColor = VtsGreen,
                contentColor = Color(0xFFFFF5E1)
            ) { Icon(Icons.Filled.Add, contentDescription = "Add contact") }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScreenDividers.Thick()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    contacts,
                    key = { _, c -> "${c.name.lowercase()}|${c.phone}" }
                ) { index, c ->
                    ContactRow(
                        contact = c,
                        menuOpen = menuOpenFor == c.name,
                        onOpenMenu = { menuOpenFor = c.name },
                        onDismissMenu = { menuOpenFor = null },
                        onCall = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                            context.startActivity(intent)               // ← use captured context
                        },
                        onEdit = { editing = c },
                        onDelete = {
                            ImportantContactStore.delete(appContext, c.name, c.phone) // ← pass phone
                            contacts = ImportantContactStore.load(appContext)
                            scope.launch { snackbar.showSnackbar("Deleted \"${c.name}\"") }
                        }
                    )
                    if (index < contacts.lastIndex) ScreenDividers.Thin(inset = 12.dp)
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


@Composable
private fun ContactRow(
    contact: ImportantContact,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onCall: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                    modifier = Modifier.clickable { onCall(contact.phone) },
                    maxLines = 1
                )
            }

            IconButton(onClick = { onCall(contact.phone) }) {
                Icon(Icons.Filled.Phone, contentDescription = "Call")
            }

            Box {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { onDismissMenu(); onEdit() },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { onDismissMenu(); onDelete() },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
                    )
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
