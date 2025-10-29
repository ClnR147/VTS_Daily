package com.example.vtsdaily

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
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
import com.example.vtsdaily.ui.theme.VTSDailyTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep your MANAGE_EXTERNAL_STORAGE prompt
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            VTSDailyTheme {
                var showDrivers by rememberSaveable { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    if (showDrivers) "Drivers" else "Schedule",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            actions = {
                                TextButton(onClick = { showDrivers = !showDrivers }) {
                                    Text(if (showDrivers) "Schedule" else "Drivers")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        if (showDrivers) {
                            DriversScreen()
                        } else {
                            PassengerApp()
                        }
                    }
                }
            }
        }
    }
}
