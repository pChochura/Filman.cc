package com.example.filman.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer

@Stable
internal class FocusRestorationState(
    val focusRequester: FocusRequester,
    val lastFocusedItemKey: String?,
)

internal val LocalFocusRestorationState = staticCompositionLocalOf<FocusRestorationState?> { null }

@Composable
internal fun Modifier.withFocusRestoration(
    itemKey: String,
): Modifier {
    val restorationState = LocalFocusRestorationState.current ?: return this

    return if (restorationState.lastFocusedItemKey == itemKey) {
        this.focusRequester(restorationState.focusRequester)
    } else {
        this
    }
}

@Composable
internal fun Modifier.sectionFocusRestorer(
    sectionKeyPrefix: String,
    defaultFallback: FocusRequester = FocusRequester.Default,
): Modifier {
    val restorationState =
        LocalFocusRestorationState.current ?: return this.focusRestorer(defaultFallback)

    val fallback = remember(
        restorationState.lastFocusedItemKey,
        restorationState.focusRequester,
        defaultFallback,
    ) {
        if (restorationState.lastFocusedItemKey?.startsWith(sectionKeyPrefix) == true) {
            restorationState.focusRequester
        } else {
            defaultFallback
        }
    }

    return this.focusRestorer(fallback)
}
