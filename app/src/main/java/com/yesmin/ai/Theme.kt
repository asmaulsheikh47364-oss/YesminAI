package com.yesmin.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB69DFF), onPrimary = Color(0xFF2B0A6B),
    primaryContainer = Color(0xFF3F2A7A), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF82B1FF), tertiary = Color(0xFFFF9EC7),
    background = Color(0xFF12101A), onBackground = Color(0xFFE8E2F2),
    surface = Color(0xFF1C1826), onSurface = Color(0xFFE8E2F2),
    surfaceVariant = Color(0xFF2A2538), onSurfaceVariant = Color(0xFFCFC8DC)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A3DE8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF2962FF), tertiary = Color(0xFFD81B76),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFEDE7FA), onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun YesminTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
