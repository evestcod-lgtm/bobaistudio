package com.bobai.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Bob AI Studio brand palette: near-black canvas, red accents (CapCut-style),
// a cyan pop for secondary actions/highlights.
val BobBlack = Color(0xFF0A0A0A)
val BobSurface = Color(0xFF1C1C1E)
val BobSurfaceLight = Color(0xFF2A2A2D)
val BobRed = Color(0xFFFF2E2E)
val BobRedDark = Color(0xFFB30000)
val BobCyan = Color(0xFF37E5E5)
val BobTextPrimary = Color(0xFFF5F5F5)
val BobTextSecondary = Color(0xFFA0A0A5)

private val BobDarkColors = darkColorScheme(
    primary = BobRed,
    onPrimary = Color.White,
    secondary = BobCyan,
    onSecondary = Color.Black,
    background = BobBlack,
    onBackground = BobTextPrimary,
    surface = BobSurface,
    onSurface = BobTextPrimary,
    surfaceVariant = BobSurfaceLight,
    error = BobRed,
)

@Composable
fun BobAiStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BobDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
