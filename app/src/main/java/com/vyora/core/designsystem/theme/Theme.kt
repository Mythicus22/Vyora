package com.vyora.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VyoraDarkColorScheme = darkColorScheme(
    primary = VyoraOrangePrimary,
    onPrimary = Color(0xFF5D1900),
    primaryContainer = VyoraEmergencyOrange,
    onPrimaryContainer = Color(0xFF390C00),

    secondary = VyoraCyanSecondary,
    onSecondary = Color(0xFF003640),
    secondaryContainer = VyoraCyanDim,
    onSecondaryContainer = Color(0xFF001F26),

    tertiary = VyoraMeshGreen,
    onTertiary = Color(0xFF003824),
    tertiaryContainer = VyoraMeshGreenDim,
    onTertiaryContainer = Color(0xFF002113),

    background = VyoraNavyDark,
    onBackground = VyoraTextPrimary,
    surface = VyoraNavyDark,
    onSurface = VyoraTextPrimary,
    surfaceVariant = VyoraNavyHighlight,
    onSurfaceVariant = VyoraTextSecondary,

    outline = VyoraNavyBorder,
    outlineVariant = Color(0xFF1B2E42),

    error = VyoraError,
    onError = Color(0xFF690005),
    errorContainer = VyoraErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun VyoraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VyoraDarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
