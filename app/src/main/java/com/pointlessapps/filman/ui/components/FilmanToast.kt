package com.pointlessapps.filman.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FilmanToast(
    message: String,
    modifier: Modifier = Modifier,
    durationMs: Long = 3000L,
    onDismiss: () -> Unit = {},
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(message) {
        isVisible = true
        delay(durationMs.milliseconds)
        isVisible = false
        delay(300.milliseconds)
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small,
                ),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
