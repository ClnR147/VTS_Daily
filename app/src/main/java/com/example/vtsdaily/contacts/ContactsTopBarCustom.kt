// ContactsTopBarCustom.kt
package com.example.vtsdaily.contacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.ui.components.VtsTopAppBar

@Composable
fun ContactsTopBarCustom(
    title: String = "Contacts",
    onAdd: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onImportCsv: () -> Unit,
    onImportJson: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    VtsTopAppBar(
        title = title,
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add Contact") },
                    onClick = { menuOpen = false; onAdd() }
                )
                Divider()
                DropdownMenuItem(
                    text = { Text("Backup to External") },
                    onClick = { menuOpen = false; onBackup() }
                )
                DropdownMenuItem(
                    text = { Text("Restore from External") },
                    onClick = { menuOpen = false; onRestore() }
                )
                Divider()
                DropdownMenuItem(
                    text = { Text("Import CSV…") },
                    onClick = { menuOpen = false; onImportCsv() }
                )
                DropdownMenuItem(
                    text = { Text("Import JSON…") },
                    onClick = { menuOpen = false; onImportJson() }
                )
            }
        }
    )
}
