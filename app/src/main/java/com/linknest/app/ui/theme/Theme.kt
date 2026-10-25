package com.linknest.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LinkNestDarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3D2D72),
    onPrimaryContainer = PurpleBright,
    secondary = AccentBlue,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = Color(0xFFFF9B9B),
    onError = Color(0xFF3D0B0B)
)

@Composable
fun LinkNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LinkNestDarkColors,
        typography = LinkNestTypography,
        shapes = LinkNestShapes,
        content = content
    )
}
