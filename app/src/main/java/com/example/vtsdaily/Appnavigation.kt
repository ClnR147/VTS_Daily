// AppNavigation.kt
// AppNavigation.kt
package com.example.vtsdaily

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.navigation.compose.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "passengers") {
        composable("passengers") { /* Passenger screen + pill */ }

    }
}
