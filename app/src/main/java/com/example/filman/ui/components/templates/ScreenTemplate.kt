package com.example.filman.ui.components.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.ui.theme.spacing

@Composable
fun ScreenTemplate(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    background: @Composable BoxScope.() -> Unit = { DefaultBackground() },
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background Slot
        background()

        // Content Slot
        if (!isLoading && error == null) {
            content()
        }

        // Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Error Overlay
        if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    )
}
