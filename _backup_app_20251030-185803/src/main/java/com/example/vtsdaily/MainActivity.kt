package com.example.vtsdaily

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.vtsdaily.drivers.DriversScreen
import com.example.vtsdaily.lookup.PassengerLookupScreen
import com.example.vtsdaily.ui.theme.VTSDailyTheme

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
                            },
                            actions = {
                                Row {
                                    TextButton(onClick = { view = 0 }) { Text("Schedule") }
                                    TextButton(onClick = { view = 1 }) { Text("Drivers") }
                                    TextButton(onClick = { view = 2 }) { Text("Lookup") }
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (view) {
                            0 -> PassengerApp()
                            1 -> DriversScreen()
                            2 -> PassengerLookupScreen()
                        }
                    }
                }
            }
        }
    }
}
