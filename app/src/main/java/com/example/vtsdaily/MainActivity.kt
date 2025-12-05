package com.example.vtsdaily

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.contacts.ContactsScreen
import com.example.vtsdaily.contacts.ContactsTopBarCustom
import com.example.vtsdaily.drivers.DriversScreen
import com.example.vtsdaily.drivers.DriversTopBarCustom
import com.example.vtsdaily.lookup.PassengerLookupScreen
import com.example.vtsdaily.lookup.PassengerLookupTopBarCustom
import com.example.vtsdaily.ui.components.ScreenDividers
import com.example.vtsdaily.ui.theme.VTSDailyTheme
import androidx.compose.material3.ButtonDefaults

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search

// Subtle dark layer to push the scene toward medium-dark
val darkScrim = Color.Black.copy(alpha = 0.06f) // tweak 0.04–0.10 to taste

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
            VTSDailyTheme(dynamicColor = false) {
                // 0 = Schedule, 1 = Lookup, 2 = Drivers, 3 = Contacts
                var view by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Top bar callbacks registered from child screens
                var driversAdd by remember { mutableStateOf<() -> Unit>({}) }
                var driversImport by remember { mutableStateOf<() -> Unit>({}) }
                var lookupByDateAction by remember { mutableStateOf<() -> Unit>({}) }
                var lookupPredictAction by remember { mutableStateOf<() -> Unit>({}) }
                var lookupImportAction by remember { mutableStateOf<() -> Unit>({}) }
                var contactsAdd by remember { mutableStateOf<() -> Unit>({}) }
                var contactsBackup by remember { mutableStateOf<() -> Unit>({}) }
              //  var contactsRestore by remember { mutableStateOf<() -> Unit>({}) }
              //  var contactsImportCsv by remember { mutableStateOf<() -> Unit>({}) }
              //  var contactsImportJson by remember { mutableStateOf<() -> Unit>({}) }


                // Lookup handoff
                var setLookupQuery by remember { mutableStateOf<(String) -> Unit>({}) }
                var pendingLookupName by rememberSaveable { mutableStateOf<String?>(null) }

                // Medium-dark backdrop: base vertical gradient
                val baseGrad = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.20f)
                    )
                )

                // Gentle radial glow behind content
                val glowGrad = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    radius = 900f
                )

                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,

                    topBar = {
                        when (view) {
                            0 -> CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        "Schedule",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                                )
                            )
                            1 -> PassengerLookupTopBarCustom(
                                title = "Passenger Lookup",
                                onLookupByDate = { lookupByDateAction() },
                                onPredictByDate = { lookupPredictAction() },
                                onImport = { lookupImportAction() }
                            )
                            2 -> DriversTopBarCustom(
                                title = "Drivers",
                                onAdd = { driversAdd() },
                                onImport = { driversImport() }
                            )
                            3 -> ContactsTopBarCustom(
                                title = "Contacts",
                                onAdd = { contactsAdd() },
                                onBackup = { contactsBackup() },
                                onRestore = { /* TODO: restore */ },
                                onImportCsv = { /* TODO: import CSV */ },
                                onImportJson = { /* TODO: import JSON */ }
                            )
                        }
                    },

                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            NavigationBarItem(
                                selected = view == 0,
                                onClick = { view = 0 },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Schedule") },
                                label = { Text("Schedule") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 1,
                                onClick = { view = 1 },
                                icon = { Icon(Icons.Filled.Search, contentDescription = "Lookup") },
                                label = { Text("Lookup") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 2,
                                onClick = { view = 2 },
                                icon = { Icon(Icons.Filled.Person, contentDescription = "Drivers") },
                                label = { Text("Drivers") },
                                alwaysShowLabel = true
                            )
                            NavigationBarItem(
                                selected = view == 3,
                                onClick = { view = 3 },
                                icon = { Icon(Icons.Filled.Phone, contentDescription = "Contacts") },
                                label = { Text("Contacts") },
                                alwaysShowLabel = true
                            )
                        }
                    },

                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbar,
                            modifier = Modifier.padding(12.dp)
                        ) { data ->
                            Snackbar(
                                modifier = Modifier.shadow(8.dp, MaterialTheme.shapes.large),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.large,
                                dismissAction = {
                                    TextButton(
                                        onClick = { data.dismiss() },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) { Text("Dismiss") }
                                }
                            ) {
                                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                ) { padding ->
                    // Single parent container with padding & base gradient
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(baseGrad)
                            .padding(padding)
                    ) {
                        // Backdrop layers (draw first = behind content)
                        Box(Modifier.fillMaxSize().background(glowGrad).alpha(0.85f))
                        Box(Modifier.fillMaxSize().background(darkScrim))

                        // Content
                        when (view) {
                            0 -> Box(Modifier.fillMaxSize()) {
                                PassengerApp(
                                    onLookupForName = { name ->
                                        // Stash name, then switch to Lookup
                                        pendingLookupName = sanitizeName(name)
                                        view = 1
                                    }
                                )
                                ScreenDividers.Thick()
                            }
                            1 -> PassengerLookupScreen(
                                registerActions = { onLookupByDate, onPredictByDate, onImport ->
                                    lookupByDateAction = onLookupByDate
                                    lookupPredictAction = onPredictByDate
                                    lookupImportAction = onImport
                                },
                                registerSetQuery = { setter ->
                                    // Capture setter and immediately deliver any pending name
                                    setLookupQuery = setter
                                    pendingLookupName?.let {
                                        setter(it)
                                        pendingLookupName = null
                                    }
                                }
                            )
                            2 -> DriversScreen(
                                registerActions = { addCb, importCb ->
                                    driversAdd = addCb
                                    driversImport = importCb
                                }
                            )
                            3 -> ContactsScreen(
                                registerActions = { onAdd, onBackup ->
                                    contactsAdd = onAdd
                                    contactsBackup = onBackup
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
