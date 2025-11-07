package com.example.vtsdaily.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------
// NOTE: This file intentionally does NOT declare colors like VtsGreen.
// Keep all color vals in Color.kt to avoid conflicts.
// ---------------------------------------------------------------------

// Uniquely named schemes to avoid clashes with any existing Light/DarkColorScheme
val VtsLightColorScheme: ColorScheme = lightColorScheme(
    primary = VtsGreen,
    onPrimary = Color.White,

    secondary = VtsTextPrimary_Light,
    onSecondary = Color.White,

    background = VtsBackground_Light,
    onBackground = VtsTextPrimary_Light,

    surface = VtsSurface_Light,
    onSurface = VtsTextPrimary_Light,

    surfaceVariant = VtsBackground_Light,
    onSurfaceVariant = VtsTextSecondary_Light,

    outline = VtsOutline_Light,
    error = VtsError
)

val VtsDarkColorScheme: ColorScheme = darkColorScheme(
    primary = VtsGreen,
    onPrimary = Color.Black,

    secondary = VtsGreen,
    onSecondary = Color.Black,

    background = VtsBackground_Dark,
    onBackground = VtsText_OnDark,

    surface = VtsSurface_Dark,
    onSurface = VtsText_OnDark,

    surfaceVariant = VtsBackground_Dark,
    onSurfaceVariant = VtsText_OnDark,

    outline = VtsOutline_Dark,
    error = VtsError
)

@Composable
fun VTSDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VtsDarkColorScheme
        else -> VtsLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    // ——— Divider themes (Card + Screen) provided here ———
    val cardDividerStyle = defaultCardDividerStyle().copy(
        color = VtsGreen.copy(alpha = 0.85f),   // strong, inside cards
        horizontalInset = 12.dp                  // match your card inner padding
    )
    val screenDividerStyle = defaultScreenDividerStyle().copy(
        color = VtsGreen.copy(alpha = 0.85f),
        thick = 8.dp,            // thick brand divider
        horizontalInset = 12.dp, // aligns with field padding
        verticalSpace = 0.dp
    )

    CompositionLocalProvider(
        LocalCardDividerStyle provides cardDividerStyle,
        LocalScreenDividerStyle provides screenDividerStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = androidx.compose.material3.Shapes(),
            content = content
        )
    }
}
