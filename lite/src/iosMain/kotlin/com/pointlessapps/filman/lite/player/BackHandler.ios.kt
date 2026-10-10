package com.pointlessapps.filman.lite.player

import androidx.compose.runtime.Composable

// iOS has no system back button; the player overlay has an explicit Close button.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
