package com.example.filman.ui.login

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
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
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.config.FilmanConfig
import com.example.filman.ui.components.FilmanButton
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.selectablePulse
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun LoginScreen(
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

    LoginScreenBackground(state.backgroundImages)
    LoginScreenWebView(
        isManualSolveRequired = isManualSolveRequired,
        isLoginLoading = { state.isLoginLoading },
        onAuthFailed = {
            onEvent(LoginEvent.OnLoginFailed)
            isCredentialsError = true
            contentFocusRequester.requestFocus()
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

@Composable
private fun LoginScreenInputContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    webViewRef: WebView?,
    isCredentialsError: Boolean,
    onIsCredentialsErrorChanged: (Boolean) -> Unit,
    onIsManualSolveRequiredChanged: (Boolean) -> Unit,
    contentFocusRequester: FocusRequester,
) {
    val coroutineScope = rememberCoroutineScope()
    val usernameState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.login_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.large))
    }

    LoginScreenInput(
        state = usernameState,
        label = R.string.login_username,
        textFieldFocusRequester = contentFocusRequester,
        showDoneAction = false,
        isPassword = false,
        isError = isCredentialsError,
    )
    LoginScreenInput(
        state = passwordState,
        label = R.string.login_password,
        textFieldFocusRequester = null,
        showDoneAction = true,
        isPassword = true,
        isError = isCredentialsError,
    )

    FilmanButton(
        fullWidth = true,
        isLoading = state.isLoginLoading,
        text = stringResource(R.string.login_confirm),
        iconRes = R.drawable.ic_login,
        onClick = {
            onIsCredentialsErrorChanged(false)
            val username = usernameState.text.toString()
            val password = passwordState.text.toString()
            onEvent(LoginEvent.OnLoginClicked(username, password))

            coroutineScope.launch {
                webViewRef?.bypassRecaptchaAndLogin(
                    username = username,
                    password = password,
                    onRequiresManualSolve = {
                        onEvent(LoginEvent.OnLoginFailed)
                        onIsManualSolveRequiredChanged(true)
                    },
                )
            }
        },
    )

    if (state.savedUsername != null && state.savedPassword != null) {
        Text(
            text = stringResource(R.string.login_or),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        FilmanButton(
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
            fullWidth = true,
            text = stringResource(R.string.login_as, state.savedUsername),
            iconRes = null,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = {
                onIsCredentialsErrorChanged(false)
                usernameState.setTextAndPlaceCursorAtEnd(state.savedUsername)
                passwordState.setTextAndPlaceCursorAtEnd(state.savedPassword)
                onEvent(LoginEvent.OnLoginClicked(state.savedUsername, state.savedPassword))

                coroutineScope.launch {
                    webViewRef?.bypassRecaptchaAndLogin(
                        username = state.savedUsername,
                        password = state.savedPassword,
                        onRequiresManualSolve = {
                            onEvent(LoginEvent.OnLoginFailed)
                            onIsManualSolveRequiredChanged(true)
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun LoginScreenWebView(
    isManualSolveRequired: Boolean,
    isLoginLoading: () -> Boolean,
    onAuthFailed: () -> Unit,
    onEvent: (LoginEvent) -> Unit,
    onWebViewProvided: (WebView) -> Unit,
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var boxWidth by remember { mutableIntStateOf(0) }
    var boxHeight by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isManualSolveRequired) {
        if (isManualSolveRequired) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isManualSolveRequired) 1f else 0.01f)
            .zIndex(if (isManualSolveRequired) 1f else -1f)
            .background(
                if (isManualSolveRequired) {
                    MaterialTheme.colorScheme.background
                } else {
                    Color.Transparent
                },
            )
            .onSizeChanged { size ->
                boxWidth = size.width
                boxHeight = size.height
            }
            .pointerMovement(
                boxWidthProvider = { boxWidth },
                boxHeightProvider = { boxHeight },
                onScrollRequested = { webViewInstance?.scrollBy(0, it) },
                onClickRequested = { x, y -> performClickAtCoordinates(webViewInstance, x, y) },
                enabled = isManualSolveRequired,
            )
            .focusRequester(focusRequester)
            .focusable(isManualSolveRequired),
        contentAlignment = Alignment.TopStart,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    val webViewUserAgent = WebSettings.getDefaultUserAgent(ctx)
                    settings.userAgentString = webViewUserAgent
                    webViewClient = WebViewClient(
                        isLoginLoading = isLoginLoading,
                        onCookiesFetched = { cookies ->
                            onEvent(LoginEvent.OnCookieReceived(cookies, webViewUserAgent))
                            onEvent(LoginEvent.OnAuthSuccess)
                        },
                        onAuthFailed = onAuthFailed,
                    )
                    loadUrl(FilmanConfig.LOGIN_URL)
                    onWebViewProvided(this)
                    webViewInstance = this
                }
            },
        )
    }
}

@Composable
private fun LoginScreenBackground(
    backgroundImages: List<String>,
) {
    var currentBackgroundImage by remember { mutableStateOf("") }

    LaunchedEffect(backgroundImages) {
        while (true) {
            backgroundImages.forEach { backgroundImage ->
                currentBackgroundImage = backgroundImage
                delay(5.seconds)
            }
        }
    }

    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = currentBackgroundImage,
        transitionSpec = {
            fadeIn(animationSpec = tween(1000)) togetherWith
                    fadeOut(animationSpec = tween(1000))
        },
    ) { backgroundImage ->
        val currentScaleAnimatable = remember { Animatable(1f) }
        LaunchedEffect(backgroundImage) {
            currentScaleAnimatable.animateTo(
                targetValue = 1.03f,
                animationSpec = tween(5000),
            )
        }

        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = currentScaleAnimatable.value
                    scaleY = currentScaleAnimatable.value
                },
            model = ImageRequest.Builder(LocalContext.current)
                .data(backgroundImage)
                .build(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
    }
}

@Composable
private fun LoginScreenInputBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                )
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                )
                .padding(MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.medium,
                alignment = Alignment.CenterVertically,
            ),
            content = content,
        )
    }
}

@Composable
private fun LoginScreenInput(
    state: TextFieldState,
    @StringRes label: Int,
    textFieldFocusRequester: FocusRequester?,
    showDoneAction: Boolean,
    isPassword: Boolean,
    isError: Boolean = false,
) {
    TextField(
        state = state,
        isError = isError,
        modifier = Modifier
            .then(
                if (textFieldFocusRequester != null) {
                    Modifier.focusRequester(textFieldFocusRequester)
                } else {
                    Modifier
                },
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .selectablePulse(
                shape = MaterialTheme.shapes.medium,
                focusedScale = 1f,
                pressedScale = 1f,
            ),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
        ),
        placeholder = {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = true,
            keyboardType = KeyboardType.Text,
            imeAction = if (showDoneAction) ImeAction.Done else ImeAction.Next,
            showKeyboardOnFocus = true,
        ),
        outputTransformation = OutputTransformation {
            if (isPassword) {
                this.replace(0, length, "*".repeat(length))
            }
        },
    )
}
