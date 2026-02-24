package com.example.vtsdaily

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vtsdaily.business.BusinessContactsScreen
import com.example.vtsdaily.contacts.ContactsScreen
import com.example.vtsdaily.contacts.ContactsTopBarCustom
import com.example.vtsdaily.drivers.DriversScreen
import com.example.vtsdaily.drivers.DriversTopBarCustom
import com.example.vtsdaily.lookup.PassengerLookupScreen
import com.example.vtsdaily.lookup.PassengerLookupTopBarCustom
import com.example.vtsdaily.ui.components.ScreenDividers
import com.example.vtsdaily.ui.components.VtsAfterDividerSpacing
import com.example.vtsdaily.ui.components.VtsTopAppBar
import com.example.vtsdaily.ui.theme.VTSDailyTheme

// Subtle dark layer to push the scene toward medium-dark
val darkScrim = Color.Black.copy(alpha = 0.06f) // tweak 0.04–0.10 to taste

class MainActivity : ComponentActivity() {

    // flags used to stabilize return-from-dialer
    private var returnedFromDialer = false

    // Activity-owned tab state (0=Schedule, 1=Lookup, 2=Drivers, 3=Contacts, 4=Clinics)
    private val viewState = mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request MANAGE_EXTERNAL_STORAGE on Android 11+ if not granted
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        }

        setContent {
            VTSDailyTheme(dynamicColor = false) {

                var view by viewState

                val snackbar = remember { SnackbarHostState() }

                // Top bar callbacks registered from child screens
                var driversAdd by remember { mutableStateOf<() -> Unit>({}) }
                var driversImport by remember { mutableStateOf<() -> Unit>({}) }
                var lookupByDateAction by remember { mutableStateOf<() -> Unit>({}) }
                var lookupPredictAction by remember { mutableStateOf<() -> Unit>({}) }
                var lookupImportAction by remember { mutableStateOf<() -> Unit>({}) }
                var contactsAdd by remember { mutableStateOf<() -> Unit>({}) }
                var contactsBackup by remember { mutableStateOf<() -> Unit>({}) }
                var contactsRestore by remember { mutableStateOf<() -> Unit>({}) }
                var contactsImportCsv by remember { mutableStateOf<() -> Unit>({}) }
                var contactsImportJson by remember { mutableStateOf<() -> Unit>({}) }
                var businessAdd by remember { mutableStateOf<(() -> Unit)?>(null) }
                var businessImportJson by remember { mutableStateOf<(() -> Unit)?>(null) }
                var businessExport by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                            0 -> VtsTopAppBar(title = "Schedule")

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
                                onRestore = { contactsRestore() },
                                onImportCsv = { contactsImportCsv() },
                                onImportJson = { contactsImportJson() }
                            )

                            4 -> ContactsTopBarCustom(
                                title = "Clinics",
                                onAdd = { businessAdd?.invoke() },
                                onBackup = { businessExport?.invoke() }, // export/backup
                                onRestore = { /* no-op */ },
                                onImportCsv = { /* no-op */ },
                                onImportJson = { businessImportJson?.invoke() }
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
                            NavigationBarItem(
                                selected = view == 4,
                                onClick = { view = 4 },
                                icon = { Icon(Icons.Filled.MedicalServices, contentDescription = "Clinics") },
                                label = { Text("Clinics") },
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(baseGrad)
                            .padding(padding)
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(glowGrad).alpha(0.85f))
                            Box(Modifier.fillMaxSize().background(darkScrim))

                            Column(Modifier.fillMaxSize()) {
                                ScreenDividers.Thick()
                                VtsAfterDividerSpacing()

                                Box(Modifier.fillMaxSize()) {
                                    when (view) {
                                        0 -> PassengerApp(
                                            onLookupForName = { name ->
                                                pendingLookupName = sanitizeName(name)
                                                view = 1
                                            },
                                            onDialerLaunched = {
                                                returnedFromDialer = true
                                            }
                                        )

                                        1 -> PassengerLookupScreen(
                                            registerActions = { onLookupByDate, onPredictByDate, onImport ->
                                                lookupByDateAction = onLookupByDate
                                                lookupPredictAction = onPredictByDate
                                                lookupImportAction = onImport
                                            },
                                            registerSetQuery = { setter ->
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
                                            registerActions = { onAdd, onBackup, onRestore, onImportCsv, onImportJson ->
                                                contactsAdd = onAdd
                                                contactsBackup = onBackup
                                                contactsRestore = onRestore
                                                contactsImportCsv = onImportCsv
                                                contactsImportJson = onImportJson
                                            }
                                        )

                                        4 -> BusinessContactsScreen(
                                            registerActions = { onAdd, onImportJson, onExport ->
                                                businessAdd = onAdd
                                                businessImportJson = onImportJson
                                                businessExport = onExport
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅ Correct place: OUTSIDE onCreate()
    override fun onResume() {
        super.onResume()

        if (returnedFromDialer) {
            returnedFromDialer = false
            viewState.intValue = 0
        }
    }
}