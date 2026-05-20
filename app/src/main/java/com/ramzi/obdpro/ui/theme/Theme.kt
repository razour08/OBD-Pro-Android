package com.ramzi.obdpro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * OBD Pro Material 3 Dark Theme.
 * Always dark — automotive dashboards should never use light mode.
 */
private val OBDProDarkScheme = darkColorScheme(
    primary       = NeonCyan,
    onPrimary     = DarkBackground,
    secondary     = NeonAmber,
    onSecondary   = DarkBackground,
    tertiary      = NeonMagenta,
    onTertiary    = DarkBackground,
    background    = DarkBackground,
    onBackground  = TextPrimary,
    surface       = DarkSurface,
    onSurface     = TextPrimary,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = TextSecondary,
    error         = NeonRed,
    onError       = DarkBackground,
    outline       = DarkBorder,
)

@Composable
fun OBDProTheme(content: @Composable () -> Unit) {
    val colorScheme = OBDProDarkScheme

    // Set status bar to match our dark background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OBDProTypography,
        content = content
    )
}
