// DriversTopBar.kt
package com.example.vtsdaily.drivers

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversTopBar(
    title: String = "Drivers",
    onAdd: () -> Unit,
    onImport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

        CenterAlignedTopAppBar(
        title = { Text(text = title) },
        actions = {
            OverflowMenu(menuExpanded, onDismiss = { menuExpanded = false }) {
                // Menu content
                DropdownMenuItem(
                    text = { Text("Add") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onAdd()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import") },
                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onImport()
                    }
                )
            }

            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
            }
        },
        // Optional: navigationIcon = { ... } // include if you need a back button
    )
}

@Composable
private fun RowScope.OverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        content()
    }
}

