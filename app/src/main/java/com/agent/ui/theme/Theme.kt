package com.agent.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Cyan500 = Color(0xFF00BCD4)
val Cyan700 = Color(0xFF0097A7)
val Cyan200 = Color(0xFF80DEEA)
val DarkBg = Color(0xFF121220)
val DarkSurface = Color(0xFF1E1E32)
val DarkSurfaceVariant = Color(0xFF2A2A40)
val OnDark = Color(0xFFE8E8F0)

val DarkScheme = darkColorScheme(
    primary = Cyan500,
    onPrimary = Color.Black,
    secondary = Cyan200,
    onSecondary = Color.Black,
    background = DarkBg,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0C0),
    error = Color(0xFFCF6679),
    onError = Color.Black
)
