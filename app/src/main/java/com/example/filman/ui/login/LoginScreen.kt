package com.example.filman.ui.login

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.ui.components.FilmanButton
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.selectableBorder
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    LoginScreenBackground(state.backgroundImages)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoginScreenInputBox(
            modifier = Modifier.width(IntrinsicSize.Min),
        ) {
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
                label = R.string.login_username,
                textFieldFocusRequester = contentFocusRequester,
                showDoneAction = false,
                isPassword = false,
            )
            LoginScreenInput(
                label = R.string.login_password,
                textFieldFocusRequester = null,
                showDoneAction = false,
                isPassword = true,
            )

            FilmanButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.login_confirm),
                iconRes = R.drawable.ic_login,
                onClick = {},
            )
        }
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
                .alpha(0.5f)
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

@Composable
private fun LoginScreenInput(
    @StringRes label: Int,
    textFieldFocusRequester: FocusRequester?,
    showDoneAction: Boolean,
    isPassword: Boolean,
) {
    TextField(
        state = rememberTextFieldState(),
        modifier = Modifier
            .then(
                if (textFieldFocusRequester != null) {
                    Modifier.focusRequester(textFieldFocusRequester)
                } else {
                    Modifier
                },
            )
            .selectableBorder(),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
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
