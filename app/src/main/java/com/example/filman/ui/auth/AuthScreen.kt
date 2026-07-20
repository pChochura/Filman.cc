package com.example.filman.ui.auth

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.filman.config.FilmanConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.components.atoms.ButtonStyle
import com.example.filman.ui.components.atoms.FilmanButton
import com.example.filman.ui.components.atoms.FilmanTextField
import com.example.filman.ui.components.templates.ScreenTemplate
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.theme.spacing
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun AuthRoute(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        val wifiManager =
            context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        val ip = String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff,
        )
        val port = 8080
        val url = "http://$ip:$port"
        viewModel.onEvent(AuthEvent.OnIpResolved(url))
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is AuthEffect.NavigateToHome -> onAuthSuccess()
            is AuthEffect.InjectCredentials -> {
                webViewRef?.evaluateJavascript(
                    "document.querySelector('input[name=\"login\"]').value = '${effect.user}';" +
                            "document.querySelector('input[name=\"password\"]').value = '${effect.pass}';" +
                            "var recaptcha = document.querySelector('.g-recaptcha, iframe[title*=\"recaptcha\" i]');" +
                            "if (recaptcha) { recaptcha.scrollIntoView({behavior: 'smooth', block: 'center'}); }",
                    null,
                )
            }
        }
    }

    val onEventLambda = remember { { event: AuthEvent -> viewModel.onEvent(event) } }
    val onWebViewCreatedLambda = remember { { webView: WebView -> webViewRef = webView } }

    AuthScreen(
        state = state,
        onEvent = onEventLambda,
        onWebViewCreated = onWebViewCreatedLambda,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(
    state: AuthState,
    onEvent: (AuthEvent) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
) {
    // Virtual cursor state (UI visual state)
    var cursorX by rememberSaveable { mutableFloatStateOf(400f) }
    var cursorY by rememberSaveable { mutableFloatStateOf(300f) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var boxWidth by remember { mutableFloatStateOf(1000f) }
    var boxHeight by remember { mutableFloatStateOf(800f) }
    val focusRequester = remember { FocusRequester() }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScreenTemplate {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Left Side: Instructions and QR Code
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(MaterialTheme.spacing.extraLarge),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.auth_setup_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

                FilmanTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.auth_username),
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(
                                FocusDirection.Down,
                            )
                        },
                    ),
                )
                FilmanTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.auth_password),
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onEvent(AuthEvent.OnCredentialsReceived(username, password))
                        },
                    ),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                FilmanButton(
                    onClick = { onEvent(AuthEvent.OnCredentialsReceived(username, password)) },
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.Primary,
                ) {
                    Text(
                        text = stringResource(R.string.auth_fill_credentials),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (state.savedUsername != null && state.savedPassword != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    FilmanButton(
                        onClick = {
                            onEvent(
                                AuthEvent.OnCredentialsReceived(
                                    state.savedUsername,
                                    state.savedPassword,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = ButtonStyle.Secondary,
                    ) {
                        Text(
                            text = "Login as ${state.savedUsername}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

                Text(
                    text = stringResource(R.string.auth_or) + " " + stringResource(R.string.auth_scan_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

                state.qrCodeBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.auth_qr_code),
                        modifier = Modifier.size(200.dp),
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                Text(state.localIp, style = MaterialTheme.typography.labelLarge)

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
                if (state.receivedUsername != null) {
                    Text(
                        text = stringResource(R.string.auth_credentials_received),
                        color = Color.Green,
                    )
                }
            }

            // Right Side: WebView with Virtual Cursor
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(2f)
                    .padding(MaterialTheme.spacing.medium)
                    .onSizeChanged { size ->
                        boxWidth = size.width.toFloat()
                        boxHeight = size.height.toFloat()
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val moveSpeed = 30f // speed of cursor
                        val scrollMargin = 100f
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    if (cursorY <= scrollMargin) {
                                        webViewRef?.scrollBy(0, -moveSpeed.toInt())
                                    }
                                    cursorY = max(0f, cursorY - moveSpeed)
                                    true
                                }

                                Key.DirectionDown -> {
                                    if (cursorY >= boxHeight - scrollMargin) {
                                        webViewRef?.scrollBy(0, moveSpeed.toInt())
                                    }
                                    cursorY = min(boxHeight, cursorY + moveSpeed)
                                    true
                                }

                                Key.DirectionLeft -> {
                                    if (cursorX <= 0f) return@onPreviewKeyEvent false
                                    if (cursorX <= scrollMargin) {
                                        webViewRef?.scrollBy(-moveSpeed.toInt(), 0)
                                    }
                                    cursorX = max(0f, cursorX - moveSpeed)
                                    true
                                }

                                Key.DirectionRight -> {
                                    if (cursorX >= boxWidth - scrollMargin) {
                                        webViewRef?.scrollBy(moveSpeed.toInt(), 0)
                                    }
                                    cursorX = min(boxWidth, cursorX + moveSpeed)
                                    true
                                }

                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    // Consume the key-down event (including repeats) without
                                    // dispatching a click — the actual tap fires on KeyUp below.
                                    true
                                }

                                else -> false
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    if (cursorX <= 0f) false else true
                                }

                                Key.DirectionUp, Key.DirectionDown, Key.DirectionRight -> true

                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    // Dispatch the tap on KeyUp so exactly one click is sent to
                                    // the WebView per physical key press, regardless of hold time.
                                    val downTime = SystemClock.uptimeMillis()
                                    val motionEventDown = MotionEvent.obtain(
                                        downTime,
                                        downTime,
                                        MotionEvent.ACTION_DOWN,
                                        cursorX,
                                        cursorY,
                                        0,
                                    )
                                    webViewRef?.dispatchTouchEvent(motionEventDown)
                                    motionEventDown.recycle()

                                    val motionEventUp = MotionEvent.obtain(
                                        downTime,
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_UP,
                                        cursorX,
                                        cursorY,
                                        0,
                                    )
                                    webViewRef?.dispatchTouchEvent(motionEventUp)
                                    motionEventUp.recycle()
                                    true
                                }

                                else -> false
                            }
                        } else {
                            false
                        }
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                com.example.filman.data.local.SessionManager(ctx).getUserAgent()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val cookies =
                                        CookieManager.getInstance().getCookie(FilmanConfig.BASE_URL)
                                    if (cookies != null && cookies.contains("PHPSESSID")) {
                                        if (url?.removeSuffix("/") == FilmanConfig.BASE_URL || url?.contains(
                                                "profile",
                                            ) == true || url?.contains("konto") == true
                                        ) {
                                            onEvent(AuthEvent.OnCookieReceived(cookies))
                                            onEvent(AuthEvent.OnAuthSuccess)
                                        }
                                    }
                                }
                            }
                            webViewRef = this
                            onWebViewCreated(this)
                            loadUrl(FilmanConfig.LOGIN_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Virtual Cursor Pointer
                Box(
                    modifier = Modifier
                        .offset { IntOffset(cursorX.roundToInt(), cursorY.roundToInt()) }
                        .size(MaterialTheme.spacing.medium)
                        .background(Color.Red, CircleShape),
                )
            }
        }
    }
}
