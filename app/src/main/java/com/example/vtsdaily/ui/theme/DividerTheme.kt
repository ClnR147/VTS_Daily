package com.example.vtsdaily.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Immutable
data class DividerStyle(
    val color: Color,
    val thin: Dp,
    val medium: Dp,
    val thick: Dp,
    val horizontalInset: Dp,   // left/right padding
    val verticalSpace: Dp      // top padding before the line
)

/** Separate locals for card-level vs screen-level */
internal val LocalCardDividerStyle = staticCompositionLocalOf {
    DividerStyle(
        color = Color(0x1F000000), thin = 1.dp, medium = 2.dp, thick = 3.dp,
        horizontalInset = 12.dp, verticalSpace = 8.dp
    )
}
internal val LocalScreenDividerStyle = staticCompositionLocalOf {
    DividerStyle(
        color = Color(0x33000000), thin = 1.dp, medium = 2.dp, thick = 4.dp,
        horizontalInset = 0.dp,   // full-bleed by default for screens
        verticalSpace = 10.dp
    )
}

/** Defaults derived from Material tokens */
@Composable
fun defaultCardDividerStyle(): DividerStyle =
    DividerStyle(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
        thin = 1.dp, medium = 2.dp, thick = 3.dp,
        horizontalInset = 12.dp,  // matches common card padding
        verticalSpace = 8.dp
    )

@Composable
fun defaultScreenDividerStyle(): DividerStyle =
    DividerStyle(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        thin = 1.dp, medium = 2.dp, thick = 4.dp,
        horizontalInset = 0.dp,   // full-bleed line between major sections
        verticalSpace = 10.dp
    )
