package com.mbk.loapunto.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm paper + one orange accent.
private val Light = lightColorScheme(
    primary = Color(0xFFD9622B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC8),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Color(0xFF5B6475),
    secondaryContainer = Color(0xFFE6E8EE),
    onSecondaryContainer = Color(0xFF1B2230),
    // Later: a calm, parked blue.
    tertiaryContainer = Color(0xFFD8E4F2),
    onTertiaryContainer = Color(0xFF1C3350),
    background = Color(0xFFF7F4EF),
    onBackground = Color(0xFF1E1D1B),
    surface = Color(0xFFF7F4EF),
    onSurface = Color(0xFF1E1D1B),
    surfaceVariant = Color(0xFFE9E3DA),
    onSurfaceVariant = Color(0xFF6B655C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1EDE6),
    surfaceContainer = Color(0xFFECE7DF),
    surfaceContainerHigh = Color(0xFFE6E1D8),
    surfaceContainerHighest = Color(0xFFE0DACF),
    outline = Color(0xFFCFC7BA),
    outlineVariant = Color(0xFFE2DCD2),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFA36B),
    onPrimary = Color(0xFF4A1F00),
    primaryContainer = Color(0xFF6B3410),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFB9C1D2),
    secondaryContainer = Color(0xFF2E3440),
    onSecondaryContainer = Color(0xFFDDE2EC),
    tertiaryContainer = Color(0xFF2A3A50),
    onTertiaryContainer = Color(0xFFD3E1F2),
    background = Color(0xFF141519),
    onBackground = Color(0xFFECE7E1),
    surface = Color(0xFF141519),
    onSurface = Color(0xFFECE7E1),
    surfaceVariant = Color(0xFF2A2D34),
    onSurfaceVariant = Color(0xFFB3ADA5),
    surfaceContainerLowest = Color(0xFF1E2026),
    surfaceContainerLow = Color(0xFF1A1C21),
    surfaceContainer = Color(0xFF22252B),
    surfaceContainerHigh = Color(0xFF2A2D34),
    surfaceContainerHighest = Color(0xFF33363E),
    outline = Color(0xFF4A4D55),
    outlineVariant = Color(0xFF33363E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Fixed so white labels stay readable in both themes.
val DoneGreen = Color(0xFF2E7D57)
val DeleteRed = Color(0xFFC2412D)

@Composable
fun LoApuntoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
