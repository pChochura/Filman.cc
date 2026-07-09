package com.example.filman.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val FilmanColorScheme = darkColorScheme(
    primary = PremiumPrimary,
    onPrimary = PremiumOnPrimary,
    background = PremiumBackground,
    surface = PremiumSurface,
    surfaceVariant = PremiumSurfaceVariant,
    onBackground = PremiumTextPrimary,
    onSurface = PremiumTextPrimary,
    onSurfaceVariant = PremiumTextSecondary,
)

@Composable
fun FilmanTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = FilmanColorScheme,
            typography = FilmanTypography,
            content = content,
        )
    }
}
