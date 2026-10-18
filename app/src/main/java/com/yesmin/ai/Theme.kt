package com.yesmin.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val NeonCyan = Color(0xFF22D3EE)
val NeonViolet = Color(0xFF8B5CF6)
val NeonPink = Color(0xFFF472B6)
val NeonBrush = Brush.linearGradient(listOf(NeonCyan, NeonViolet))

private val DarkColors = darkColorScheme(
    primary = NeonCyan, onPrimary = Color(0xFF00212A),
    primaryContainer = Color(0xFF1B2A5C), onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = NeonViolet, onSecondary = Color.White,
    tertiary = NeonPink,
    background = Color(0xFF070B1A), onBackground = Color(0xFFE6ECFF),
    surface = Color(0xFF0F1630), onSurface = Color(0xFFE6ECFF),
    surfaceVariant = Color(0xFF172044), onSurfaceVariant = Color(0xFFB8C4E6),
    errorContainer = Color(0xFF7A1F1F), onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0891B2), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6F3FA), onPrimaryContainer = Color(0xFF00313B),
    secondary = Color(0xFF7C3AED), onSecondary = Color.White,
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFF4F7FF), onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6ECFF), onSurfaceVariant = Color(0xFF43506B)
)

@Composable
fun YesminTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
