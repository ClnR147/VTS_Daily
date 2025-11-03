package com.example.vtsdaily.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme



// Custom small-rounded shape styling
val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp)
)

// Material 3 Color Schemes
private val DarkColorScheme = darkColorScheme(
    primary = Color (0xFF4CAF50),
    secondary = Color ( color = 0xFF4CAF50),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = OnPrimaryText,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color (0xFF4CAF50),
    secondary = Color ( color = 0xFF4CAF50),
    background = AppBackground,
    surface = SurfaceWhite,
    onPrimary = OnPrimaryText,
    onSecondary = Color.White,
    onBackground = OnSurfaceText,
    onSurface = OnSurfaceText
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
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
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
        thick = 8.dp,          // <- really thick
        horizontalInset = 12.dp, // <- same as OutlinedTextField padding
        verticalSpace = 0.dp     // no extra top gap
    )

    CompositionLocalProvider(
        LocalCardDividerStyle provides cardDividerStyle,
        LocalScreenDividerStyle provides screenDividerStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = Shapes,
            content = content
        )
    }
}
