package com.example.vtsdaily.ui

import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog  // ✅ Material 3 - correct

import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.model.ContactEntry
import java.util.*

@Composable
fun ContactDialog(
    initial: ContactEntry?,
    onDismiss: () -> Unit,
    onSave: (ContactEntry) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phoneNumber ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Contact" else "Edit Contact") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    onSave(ContactEntry(initial?.id ?: UUID.randomUUID().toString(), name, phone))
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
