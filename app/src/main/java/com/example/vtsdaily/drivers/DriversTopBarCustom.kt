package com.example.vtsdaily.drivers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.vtsdaily.ui.components.VtsTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversTopBarCustom(
    title: String,
    onAdd: () -> Unit,
    onImport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    VtsTopAppBar(               // ✅ use the shared top bar
        title = title,
        actions = {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add") },
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { expanded = false; onAdd() }
                )
                DropdownMenuItem(
                    text = { Text("Import") },
                    leadingIcon = { Icon(Icons.Filled.Upload, null) },
                    onClick = { expanded = false; onImport() }
                )
            }

            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
        }
    )
}

