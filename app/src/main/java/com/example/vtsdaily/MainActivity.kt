package com.example.vtsdaily

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.contacts.ContactsScreen
import com.example.vtsdaily.contacts.ContactsTopBarCustom
import com.example.vtsdaily.drivers.DriversScreen
import com.example.vtsdaily.lookup.PassengerLookupScreen
import com.example.vtsdaily.ui.components.ScreenDividers
import com.example.vtsdaily.ui.theme.VTSDailyTheme
import com.example.vtsdaily.lookup.PassengerLookupTopBarCustom


// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.font.FontWeight
import com.example.vtsdaily.drivers.DriversTopBarCustom

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request MANAGE_EXTERNAL_STORAGE on Android 11+ if not granted
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }

        setContent {
            VTSDailyTheme {
                // 0 = Schedule, 1 = Lookup, 2 = Drivers, 3 = Contacts
                var view by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var driversAdd by remember { mutableStateOf<() -> Unit>({}) }
                var driversImport by remember { mutableStateOf<() -> Unit>({}) }
                var lookupByDateAction by remember { mutableStateOf<() -> Unit>({}) }
                var lookupImportAction by remember { mutableStateOf<() -> Unit>({}) }



                Scaffold(
                    topBar = {
                        when (view) {
                            0 -> CenterAlignedTopAppBar(title = {Text("Schedule",style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))})
                            1 -> PassengerLookupTopBarCustom(
                                title = "Passenger Lookup",
                                onLookupByDate = { lookupByDateAction() },
                                onImport = { lookupImportAction() }
                            )
                            2 -> DriversTopBarCustom(
                                title = "Drivers",
                                onAdd = { driversAdd() },        // ← was onDriversAdd()
                                onImport = { driversImport() }   // ← was onDriversImport()
                            )
                            3 -> ContactsTopBarCustom(
                                title = "Contacts",
                                onAdd = { /* handled inside ContactsScreen */ },
                                onBackup = { /* handled inside ContactsScreen */ },
                                onRestore = { /* handled inside ContactsScreen */ },
                                onImportCsv = { /* handled inside ContactsScreen */ },
                                onImportJson = { /* handled inside ContactsScreen */ }
                            )
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF4CAF50),  // VTS Green
                            contentColor = Color(0xFFFFF5E1)     // Warm Cream
                        ) {
                            NavigationBarItem(
                                selected = view == 0,
                                onClick = { view = 0 },
                                icon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.List,
                                        contentDescription = "Schedule"
                                    )
                                },
                                label = { Text("Schedule") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 1,
                                onClick = { view = 1 },
                                icon = {
                                    Icon(Icons.Filled.Search, contentDescription = "Lookup")
                                },
                                label = { Text("Lookup") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 2,
                                onClick = { view = 2 },
                                icon = {
                                    Icon(Icons.Filled.Person, contentDescription = "Drivers")
                                },
                                label = { Text("Drivers") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 3,
                                onClick = { view = 3 },
                                icon = {
                                    Icon(Icons.Filled.Phone, contentDescription = "Contacts")
                                },
                                label = { Text("Contacts") },
                                alwaysShowLabel = true
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (view) {
                            0 -> Box(Modifier.fillMaxSize()) { PassengerApp(); ScreenDividers.Thick() }
                            1 -> PassengerLookupScreen(
                                registerActions = { onLookupByDate, onImport ->
                                    lookupByDateAction = onLookupByDate
                                    lookupImportAction = onImport
                                }
                            )
                            2 -> DriversScreen(
                                registerActions = { addCb, importCb ->
                                    driversAdd = addCb
                                    driversImport = importCb
                                }
                            )
                            3 -> ContactsScreen()
                        }
                    }
                }
            }
        }
    }
}
