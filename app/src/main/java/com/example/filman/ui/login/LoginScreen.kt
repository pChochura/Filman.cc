package com.example.filman.ui.login

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.filman.Route
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.core.CollectEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun LoginScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                contentFocusRequester.requestFocus()
            }
        }

        onPauseOrDispose { }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            else -> {}
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            LoginScreenContent(
                state = state,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
            )
        }
    }
}

@Composable
private fun LoginScreenContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    contentFocusRequester: FocusRequester,
) {

}
