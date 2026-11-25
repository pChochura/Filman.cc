package com.pointlessapps.filman.ui.login

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.TiwiButton
import com.pointlessapps.filman.ui.components.sections.TabRowSectionContent
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun LoginScreenPhonePairingContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val qrBitmap = remember(state.pairingUrl) {
        state.pairingUrl?.let { url ->
            QrCodeGenerator.generate(url, sizePx = 400)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_phone_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        Text(
            text = stringResource(R.string.login_phone_step_1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.login_phone_or_visit, "${state.pairingDomain}/pair"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        Box(
            modifier = Modifier
                .size(160.dp)
                .background(Color.White, shape = MaterialTheme.shapes.medium)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = stringResource(R.string.login_phone_title),
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.isPairingLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.Black,
                )
            }

            if (state.isPairingExpired) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000), shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.login_phone_code_expired),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        if (!state.pairingCode.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.login_phone_code_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = state.pairingCode,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        Text(
            text = stringResource(R.string.login_phone_step_2),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        TiwiButton(
            fullWidth = true,
            isLoading = state.isPairingLoading,
            text = stringResource(R.string.login_phone_refresh_code),
            iconRes = null,
            onClick = {
                onEvent(LoginEvent.OnRefreshPairingCode)
            },
        )
    }
}

@Composable
internal fun LoginScreenDivider(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.height(380.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.login_or).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
internal fun LoginScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
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
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.extraLarge,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            content = content,
        )
    }
}

@Composable
internal fun LoginScreenInputContent(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val usernameState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()

    val tabs = remember {
        listOf(
            TabRowSectionItem(title = R.string.login_login_tab, id = 0),
            TabRowSectionItem(title = R.string.login_register_tab, id = 1),
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_remote_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.extraSmall))

        Text(
            text = stringResource(R.string.login_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        TabRowSectionContent(
            items = tabs,
            selectedTabId = if (state.isRegisterMode) 1 else 0,
            onTabSelected = { tab ->
                onEvent(LoginEvent.OnModeChanged(isRegisterMode = tab.id == 1))
            },
            centered = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.authErrorMessage != null) {
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.authErrorMessage.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(MaterialTheme.spacing.medium))

        LoginScreenInput(
            state = usernameState,
            label = R.string.login_username,
            textFieldFocusRequester = contentFocusRequester,
            showDoneAction = false,
            isPassword = false,
            isError = state.authErrorMessage != null,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        LoginScreenInput(
            state = passwordState,
            label = R.string.login_password,
            textFieldFocusRequester = null,
            showDoneAction = true,
            isPassword = true,
            isError = state.authErrorMessage != null,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.large))

        TiwiButton(
            fullWidth = true,
            isLoading = state.isLoginLoading,
            text = if (state.isRegisterMode) {
                stringResource(R.string.login_register_confirm)
            } else {
                stringResource(R.string.login_confirm)
            },
            iconRes = R.drawable.ic_login,
            onClick = {
                val username = usernameState.text.toString()
                val password = passwordState.text.toString()
                onEvent(
                    LoginEvent.OnLoginClicked(
                        username = username,
                        pass = password,
                        isRegister = state.isRegisterMode,
                    ),
                )
            },
        )
    }
}

@Composable
internal fun LoginScreenBackground(backgroundImages: List<String>) {
    if (backgroundImages.isEmpty()) return
    var currentBackgroundImage by remember { mutableStateOf(backgroundImages.firstOrNull().orEmpty()) }

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
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScaleAnimatable.value
                        scaleY = currentScaleAnimatable.value
                    },
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(backgroundImage)
                    .build(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
    }
}

@Composable
internal fun LoginScreenInputBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                    ).background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                    ).padding(
                        horizontal = MaterialTheme.spacing.extraLarge,
                        vertical = MaterialTheme.spacing.extraLarge,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
internal fun LoginScreenInput(
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
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (textFieldFocusRequester != null) {
                        Modifier.focusRequester(textFieldFocusRequester)
                    } else {
                        Modifier
                    },
                ).border(
                    width = 1.dp,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ).selectablePulse(
                    shape = MaterialTheme.shapes.medium,
                    focusedScale = 1f,
                    pressedScale = 1f,
                ),
        shape = MaterialTheme.shapes.medium,
        colors =
            TextFieldDefaults.colors(
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
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = if (showDoneAction) ImeAction.Done else ImeAction.Next,
                showKeyboardOnFocus = true,
            ),
        outputTransformation =
            OutputTransformation {
                if (isPassword) {
                    this.replace(0, length, "*".repeat(length))
                }
            },
    )
}
