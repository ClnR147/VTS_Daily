package com.example.vtsdaily

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Phone // ⬅️ NEW
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize

private enum class Dest { Schedule, Lookup, Drivers, Contacts } // ⬅️ NEW

/**
 * Stable implementation of a bottom NavigationBar (compact screens)
 * and a side NavigationRail (wide screens). 100% stable Material 3 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VTSAppScaffold(
    schedule:  @Composable () -> Unit,
    lookup:    @Composable () -> Unit,
    drivers:   @Composable () -> Unit,
    contacts:  @Composable () -> Unit, // ⬅️ NEW
) {
    var current by rememberSaveable { mutableStateOf(Dest.Schedule) }

    // Use stable WindowSizeClass from material3:windowsizeclass
    val windowSizeClass = calculateWindowSizeClass()
    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (current) {
                            Dest.Schedule -> "Schedule"
                            Dest.Lookup   -> "Lookup"
                            Dest.Drivers  -> "Drivers"
                            Dest.Contacts -> "Contacts" // ⬅️ NEW
                        }
                    )
                }
            )
        },
        bottomBar = {
            if (!isWide) {
                NavigationBar {
                    NavigationBarItem(
                        selected = current == Dest.Schedule,
                        onClick = { current = Dest.Schedule },
                        icon = { Icon(Icons.Filled.CalendarMonth, null) },
                        label = { Text("Schedule") }
                    )
                    NavigationBarItem(
                        selected = current == Dest.Lookup,
                        onClick = { current = Dest.Lookup },
                        icon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Lookup") }
                    )
                    NavigationBarItem(
                        selected = current == Dest.Drivers,
                        onClick = { current = Dest.Drivers },
                        icon = { Icon(Icons.Filled.DirectionsCar, null) },
                        label = { Text("Drivers") }
                    )
                    NavigationBarItem( // ⬅️ NEW
                        selected = current == Dest.Contacts,
                        onClick = { current = Dest.Contacts },
                        icon = { Icon(Icons.Filled.Phone, null) },
                        label = { Text("Contacts") }
                    )
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {

            // Navigation rail for wide layouts
            if (isWide) {
                NavigationRail(
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    NavigationRailItem(
                        selected = current == Dest.Schedule,
                        onClick = { current = Dest.Schedule },
                        icon = { Icon(Icons.Filled.CalendarMonth, null) },
                        label = { Text("Schedule") }
                    )
                    NavigationRailItem(
                        selected = current == Dest.Lookup,
                        onClick = { current = Dest.Lookup },
                        icon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Lookup") }
                    )
                    NavigationRailItem(
                        selected = current == Dest.Drivers,
                        onClick = { current = Dest.Drivers },
                        icon = { Icon(Icons.Filled.DirectionsCar, null) },
                        label = { Text("Drivers") }
                    )
                    NavigationRailItem( // ⬅️ NEW
                        selected = current == Dest.Contacts,
                        onClick = { current = Dest.Contacts },
                        icon = { Icon(Icons.Filled.Phone, null) },
                        label = { Text("Contacts") }
                    )
                }
            }

            // Main content area
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (current) {
                    Dest.Schedule -> schedule()
                    Dest.Lookup   -> lookup()
                    Dest.Drivers  -> drivers()
                    Dest.Contacts -> contacts() // ⬅️ NEW
                }
            }
        }
    }
}

/**
 * Utility function that works in both activities and previews
 * without needing an Activity reference.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun calculateWindowSizeClass(): WindowSizeClass {
    val density = LocalDensity.current
    val metrics = LocalConfiguration.current
    val widthDp = metrics.screenWidthDp.dp
    val heightDp = metrics.screenHeightDp.dp
    return WindowSizeClass.calculateFromSize(
        DpSize(widthDp, heightDp)
    )
}
