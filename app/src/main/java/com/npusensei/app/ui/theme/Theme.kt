package com.npusensei.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SenseiLightColorScheme = lightColorScheme(
    primary = SenseiGreen,
    secondary = SenseiMint,
    tertiary = SenseiSky,
    background = SenseiBackground,
    surface = SenseiSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SenseiInk,
    onSurface = SenseiInk,
    onSurfaceVariant = Color(0xFF6B7B75),
    surfaceVariant = Color(0xFFF0F4F2),
    outline = Color(0xFFD4DED8),
)

@Composable
fun NPUSenseiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SenseiLightColorScheme,
        typography = Typography,
        content = content,
    )
}
