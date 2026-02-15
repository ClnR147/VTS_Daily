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

val BusinessRowStripe = androidx.compose.ui.graphics.Color(0xFFF7F5FA)

/** Content-only screen (MainActivity owns the Scaffold/topBar) */
@Composable
fun BusinessContactsScreen(
    registerActions: ((onAdd: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var contacts by remember { mutableStateOf(BusinessContactStore.load(appContext)) }
    var editing by remember { mutableStateOf<BusinessContact?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val onAddClicked: () -> Unit = {
        editing = BusinessContact(name = "", address = "", phone = "")
    }

    LaunchedEffect(Unit) {
        registerActions?.invoke(onAddClicked)
    }

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
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contact.address.isNotBlank()) {
                    Text(
                        contact.address,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.combinedClickable(
                        onClick = { if (contact.phone.isNotBlank()) onCall(contact.phone) },
                        onLongClick = { if (contact.phone.isNotBlank()) clipboard.setText(AnnotatedString(contact.phone)) }
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
