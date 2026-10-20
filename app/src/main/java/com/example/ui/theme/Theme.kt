package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = Color.White,
    primaryContainer = LightTeal,
    onPrimaryContainer = DarkTeal,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    background = PageBackground,
    onBackground = PrimaryText,
    surface = SurfaceWhite,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = SecondaryText,
    outline = SubtleBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = SecondaryTeal,
    onPrimary = Color.White,
    primaryContainer = DarkTeal,
    onPrimaryContainer = LightTeal,
    secondary = LightTeal,
    onSecondary = DarkTeal,
    background = Color(0xFF111719),
    onBackground = Color(0xFFF1F5F7),
    surface = Color(0xFF182023),
    onSurface = Color(0xFFF1F5F7),
    surfaceVariant = Color(0xFF222B2F),
    onSurfaceVariant = Color(0xFF9AA7AD),
    outline = Color(0xFF2E383D)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Keep reference daylight appearance consistent with prompt
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
