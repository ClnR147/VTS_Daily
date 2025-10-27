package com.vts.drivervans

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val RouteDrivers = "drivers"

fun NavGraphBuilder.registerDriversDestination() {
    composable(RouteDrivers) { DriversScreen() }
}

