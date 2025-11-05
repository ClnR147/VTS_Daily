// com.example.vtsdaily.contacts/ContactRow.kt
package com.example.vtsdaily.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ContactRow(
    contact: ImportantContact,
    onEdit: (ImportantContact) -> Unit,
    onDelete: (ImportantContact) -> Unit,
    onCall: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val doCall: (String) -> Unit = onCall ?: { phone ->
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        ctx.startActivity(intent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { doCall(contact.phone) }) {
            Icon(Icons.Filled.Phone, contentDescription = "Call")
        }
        IconButton(onClick = { onEdit(contact) }) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = { onDelete(contact) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}
