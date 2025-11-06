package com.example.vtsdaily.contacts

import android.content.Intent
import android.net.Uri
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
fun ContactsScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var contacts by remember { mutableStateOf(ImportantContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<ImportantContact?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // File pickers (stay here; actions can still be triggered by the top bar in MainActivity later if you hoist callbacks)
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

    // BODY
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Keep your thick divider to match other screens
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

        // Local Snackbar host (since we removed Scaffold here)
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // Add/Edit dialog
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

    // (Optional) If you want to trigger imports from MainActivity’s top bar later, we can hoist
    // small callbacks or a controller. For now, imports remain available via these pickers.
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
