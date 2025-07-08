package com.example.vtsdaily.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vtsdaily.data.ContactStore
import com.example.vtsdaily.model.ContactEntry

@Composable
fun ContactListScreen(context: Context) {
    var contacts by remember { mutableStateOf(ContactStore.load(context)) }
    var editing by remember { mutableStateOf<ContactEntry?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Important Contacts", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(contacts, key = { it.id }) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${contact.phoneNumber}")
                            }
                            context.startActivity(intent)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(contact.name, fontWeight = FontWeight.Bold)
                        Text(contact.phoneNumber, fontSize = 14.sp)
                    }
                    Row {
                        IconButton(onClick = {
                            editing = contact
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = {
                            ContactStore.delete(context, contact.id)
                            contacts = ContactStore.load(context)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }

        Button(onClick = {
            editing = null
            showDialog = true
        }) {
            Text("Add Contact")
        }
    }

    if (showDialog) {
        ContactDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = {
                ContactStore.addOrUpdate(context, it)
                contacts = ContactStore.load(context)
                showDialog = false
            }
        )
    }
}
