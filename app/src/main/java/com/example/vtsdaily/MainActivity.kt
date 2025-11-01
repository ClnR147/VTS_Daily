package com.example.vtsdaily

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.vtsdaily.drivers.DriversScreen
import com.example.vtsdaily.lookup.PassengerLookupScreen
import com.example.vtsdaily.ui.theme.VTSDailyTheme

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon

import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request MANAGE_EXTERNAL_STORAGE on Android 11+ if not granted
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            VTSDailyTheme {
                // 0 = Schedule, 1 = Drivers, 2 = Lookup
                var view by rememberSaveable { mutableStateOf(0) }
                val titles = listOf("Schedule", "Drivers", "Passenger Lookup")

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    titles[view],
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF4CAF50),  // VTSGreen
                            contentColor = Color(0xFFFFF5E1)     // Warm Cream
                        ) {
                            NavigationBarItem(
                                selected = view == 0,
                                onClick = { view = 0 },
                                icon = { Icon(Icons.Filled.List, contentDescription = "Schedule") },
                                label = { Text("Schedule") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 1,
                                onClick = { view = 1 },
                                icon = { Icon(Icons.Filled.Person, contentDescription = "Drivers") },
                                label = { Text("Drivers") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 2,
                                onClick = { view = 2 },
                                icon = { Icon(Icons.Filled.Search, contentDescription = "Lookup") },
                                label = { Text("Lookup") },
                                alwaysShowLabel = true
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (view) {
                            0 -> Box(Modifier.fillMaxSize()) {
                                // Draw content first
                                val gutter = 16.dp
                                PassengerApp() // your existing Schedule UI (date, table, etc.)

                                // Overlay the divider so it appears between header and date
                                HorizontalDivider(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(start = gutter, end = gutter)
                                        .zIndex(1f),
                                    thickness = 8.dp,
                                    color = Color(0xFF4CAF50) // VtsGreen
                                )
                            }
                            1 -> DriversScreen()
                            2 -> PassengerLookupScreen()
                        }
                    }
                }
            }
        }
    }
}
