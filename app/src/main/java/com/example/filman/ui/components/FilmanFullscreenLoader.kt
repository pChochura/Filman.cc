package com.example.filman.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme

@Composable
internal fun FilmanFullscreenLoader(
    isVisibleProvider: () -> Boolean = { true },
) {
    if (isVisibleProvider()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
        )
    }
}
