package com.example.vtsdaily.lookup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vtsdaily.ui.components.ScreenDividers
import androidx.compose.material3.TopAppBarDefaults


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLookupTopBarCustom(
    title: String? = null,
    onLookupByDate: () -> Unit,
    onImport: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            title = {
                Text(
                    title ?: "Passenger Lookup",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            actions = {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Import CSV") },
                        leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onImport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Lookup by Date") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onLookupByDate()
                        }
                    )
                }
            }
        )


        // Match the other screens with a thick divider under the bar
        ScreenDividers.Thick()

    }
}
