package com.example.vtsdaily.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material.icons.outlined.WheelchairPickup
import androidx.compose.material.icons.outlined.Blind
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TripNoteBadges(flags: TripNoteFlags, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {

        // passenger requests / access
        if (flags.callOnArrival) {
            Icon(Icons.Outlined.Call, contentDescription = "Call on arrival", tint = tint, modifier = Modifier.size(18.dp))
        }
        if (flags.hasGateCode) {
            Icon(Icons.Outlined.Keyboard, contentDescription = "Gate code", tint = tint, modifier = Modifier.size(18.dp))
        }

        // mobility
        if (flags.needsRamp) {
            Icon(Icons.Outlined.WheelchairPickup, contentDescription = "Needs ramp", tint = tint, modifier = Modifier.size(18.dp))
        }

        if (flags.blind) {
            Icon(Icons.Outlined.Blind, contentDescription = "Blind", tint = tint, modifier = Modifier.size(18.dp))
        }

        if (flags.pets) {
            Icon(Icons.Outlined.Pets, contentDescription = "Pet", tint = tint, modifier = Modifier.size(18.dp))
        }

        if (flags.needsLift) {
            Icon(Icons.Outlined.Upgrade, contentDescription = "Needs lift", tint = tint, modifier = Modifier.size(18.dp))
        }

        if (flags.usesCane) {
            Icon(Icons.Outlined.AccessibilityNew, contentDescription = "Uses cane", tint = tint, modifier = Modifier.size(18.dp))
        }

        // equipment
        if (flags.bringCarSeat) {
            Icon(Icons.Outlined.ChildCare, contentDescription = "Bring car seat", tint = tint, modifier = Modifier.size(18.dp))
        }

        // pickup location hints
        if (flags.pickupFront) {
            Icon(Icons.Outlined.DoorFront, contentDescription = "Front pickup", tint = tint, modifier = Modifier.size(18.dp))
        }
        if (flags.pickupBack) {
            Icon(Icons.Outlined.Stairs, contentDescription = "Back pickup", tint = tint, modifier = Modifier.size(18.dp))
        }
        if (flags.pickupAlley) {
            Icon(Icons.Outlined.LocalShipping, contentDescription = "Alley pickup", tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}
