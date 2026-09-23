package com.pointlessapps.filman.ui.login

import com.pointlessapps.filman.ui.core.bypassRecaptchaAndLogin
import com.pointlessapps.filman.ui.core.pointerMovement
import com.pointlessapps.filman.ui.core.performClickAtCoordinates
import com.pointlessapps.filman.ui.core.WebViewClient

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
                contentFocusRequester.requestFocus()
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
            LoginScreenContent(
                state = state,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreenContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    contentFocusRequester: FocusRequester,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isManualSolveRequired by remember { mutableStateOf(false) }
    var isCredentialsError by remember { mutableStateOf(false) }

    BackHandler(isManualSolveRequired) {
        isManualSolveRequired = false
        onEvent(LoginEvent.OnLoginFailed)
    }

    LoginScreenBackground(state.backgroundImages)
    LoginScreenWebView(
        isManualSolveRequired = isManualSolveRequired,
        isLoginLoading = { state.isLoginLoading },
        onAuthFailed = {
            onEvent(LoginEvent.OnLoginFailed)
            isCredentialsError = true
            isManualSolveRequired = false
            contentFocusRequester.requestFocus()
        },
        onRequiresManualSolve = {
            onEvent(LoginEvent.OnLoginFailed)
            isManualSolveRequired = true
        },
        onEvent = onEvent,
        onWebViewProvided = { webViewRef = it },
    )

    if (!isManualSolveRequired) {
        LoginScreenInputBox(
            modifier = Modifier.width(IntrinsicSize.Min),
        ) {
            LoginScreenInputContent(
                state = state,
                onEvent = onEvent,
                webViewRef = webViewRef,
                isCredentialsError = isCredentialsError,
                onIsCredentialsErrorChanged = { isCredentialsError = it },
                onIsManualSolveRequiredChanged = { isManualSolveRequired = it },
                contentFocusRequester = contentFocusRequester,
            )
        }
    }
}

