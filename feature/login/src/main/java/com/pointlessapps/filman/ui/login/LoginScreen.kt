package com.pointlessapps.filman.ui.login

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.core.CollectEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LoginScreen(
    returnRoute: Route?,
    onNavigateTo: (Route?) -> Unit,
    contentFocusRequester: FocusRequester,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                runCatching { contentFocusRequester.requestFocus() }
            }
        }

        onPauseOrDispose { }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            LoginEffect.NavigateBack -> {
                if (returnRoute != null) {
                    onNavigateTo(returnRoute)
                } else {
                    onNavigateTo(null)
                }
            }
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            LoginScreenBackground(state.backgroundImages)
            LoginScreenInputBox(
                modifier = Modifier.width(380.dp),
            ) {
                LoginScreenInputContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    contentFocusRequester = contentFocusRequester,
                )
            }
        }
    }
}
