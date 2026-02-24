package com.example.vtsdaily.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standard vertical rhythm directly under the global Thick divider.
 * Use this once, immediately after ScreenDividers.Thick().
 */
@Composable
fun VtsAfterDividerSpacing() {
    Spacer(Modifier.height(8.dp))
}
