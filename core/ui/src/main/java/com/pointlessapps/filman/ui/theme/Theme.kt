package com.pointlessapps.filman.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TiwiColorScheme =
    darkColorScheme(
        primary = PremiumPrimary,
        onPrimary = PremiumOnPrimary,
        background = PremiumBackground,
        surface = PremiumSurface,
        surfaceVariant = PremiumSurfaceVariant,
        onBackground = PremiumTextPrimary,
        onSurface = PremiumTextPrimary,
        onSurfaceVariant = PremiumTextSecondary,
    )

private val FilmanColorScheme = TiwiColorScheme

@Composable
fun TiwiTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = TiwiColorScheme,
            typography = TiwiTypography,
            content = content,
        )
    }
}

@Composable
fun FilmanTheme(content: @Composable () -> Unit) = TiwiTheme(content)
