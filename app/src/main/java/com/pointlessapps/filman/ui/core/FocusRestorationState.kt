package com.pointlessapps.filman.ui.core

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
    val lastFocusedItemKeys: List<String>,
)

internal val LocalFocusRestorationState = staticCompositionLocalOf<FocusRestorationState?> { null }

@Composable
internal fun Modifier.withFocusRestoration(itemKey: String): Modifier {
    val restorationState = LocalFocusRestorationState.current ?: return this

    return this.focusRequester(
        if (restorationState.lastFocusedItemKeys.lastOrNull() == itemKey) {
            restorationState.focusRequester
        } else {
            FocusRequester.Default
        },
    )
}

@Composable
internal fun Modifier.sectionFocusRestorer(
    sectionKeyPrefix: String,
    defaultFallback: FocusRequester = FocusRequester.Default,
): Modifier {
    val restorationState =
        LocalFocusRestorationState.current ?: return this.focusRestorer(defaultFallback)

    val fallback = remember(
        restorationState.lastFocusedItemKeys,
        restorationState.focusRequester,
        defaultFallback,
    ) {
        if (restorationState.lastFocusedItemKeys.lastOrNull()
                ?.startsWith(sectionKeyPrefix) == true
        ) {
            restorationState.focusRequester
        } else {
            defaultFallback
        }
    }

    return this.focusRestorer(fallback)
}

internal enum class SectionFocusRestorationId(val prefix: String) {
    FEATURED("featured_"),
    EPISODES("episodes_"),
    CREW("crew_"),
    CONTINUE_WATCHING("continue_"),
    RECOMMENDED("recommended_");

    companion object {
        fun moviesRowPrefix(titleId: String): String = "movies_row_${titleId}_"
    }
}
