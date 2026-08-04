package com.pointlessapps.filman.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun FilmanFullscreenLoader(
    isVisibleProvider: () -> Boolean = { true },
    longLoadingContent: (@Composable () -> Unit)? = null,
) {
    if (isVisibleProvider()) {
        var showLongLoadingContent by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(LONG_LOADING_TIMEOUT_MS.milliseconds)
            showLongLoadingContent = true
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = {
                var indicatorSize by remember { mutableIntStateOf(0) }
                var longLoadingContentHeight by remember { mutableIntStateOf(0) }
                CircularProgressIndicator(
                    modifier = Modifier.onSizeChanged {
                        indicatorSize = maxOf(it.width, it.height)
                    },
                    color = MaterialTheme.colorScheme.primary,
                )

                AnimatedVisibility(
                    modifier = Modifier
                        .onSizeChanged { longLoadingContentHeight = it.height }
                        .padding(
                            top = with(LocalDensity.current) {
                                (indicatorSize + longLoadingContentHeight).toDp()
                            },
                        ),
                    visible = showLongLoadingContent && longLoadingContent != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    content = { longLoadingContent?.invoke() },
                )
            },
        )
    }
}

private const val LONG_LOADING_TIMEOUT_MS = 5000
