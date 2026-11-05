package com.angelvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AngelVPN palette (matches web client)
val Ink = Color(0xFF0C1110)
val Surface = Color(0xFF141A18)
val Surface2 = Color(0xFF1C2421)
val Line = Color(0xFF2A3330)
val Gold = Color(0xFFD6A15A)
val GoldDim = Color(0xFFB8863E)
val Signal = Color(0xFF3DBE9A)
val Danger = Color(0xFFE85D5D)
val Fg = Color(0xFFE8EEEB)
val Muted = Color(0xFF8A9691)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = Signal,
    onSecondary = Ink,
    background = Ink,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Danger,
    onError = Fg
)

@Composable
fun AngelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
