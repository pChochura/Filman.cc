package com.example.filman.ui.auth

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
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
    var cursorX by remember { mutableFloatStateOf(400f) }
    var cursorY by remember { mutableFloatStateOf(300f) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var isUsernameFocused by remember { mutableStateOf(false) }
    var isPasswordFocused by remember { mutableStateOf(false) }
    var boxWidth by remember { mutableFloatStateOf(1000f) }
    var boxHeight by remember { mutableFloatStateOf(800f) }
    val focusRequester = remember { FocusRequester() }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            ),
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

            BasicTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isUsernameFocused = it.isFocused }
                    .border(
                        width = if (isUsernameFocused) 2.dp else 1.dp,
                        color = if (isUsernameFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
                decorationBox = { innerTextField ->
                    if (username.isEmpty()) {
                        Text(
                            text = stringResource(R.string.auth_username),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onEvent(AuthEvent.OnCredentialsReceived(username, password))
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isPasswordFocused = it.isFocused }
                    .border(
                        width = if (isPasswordFocused) 2.dp else 1.dp,
                        color = if (isPasswordFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
                decorationBox = { innerTextField ->
                    if (password.isEmpty()) {
                        Text(
                            text = stringResource(R.string.auth_password),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Button(
                onClick = { onEvent(AuthEvent.OnCredentialsReceived(username, password)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    focusedContentColor = Color.White
                ),
                shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
            ) {
                Text(stringResource(R.string.auth_fill_credentials), style = MaterialTheme.typography.titleMedium)
            }

            if (state.savedUsername != null && state.savedPassword != null) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                Button(
                    onClick = { onEvent(AuthEvent.OnCredentialsReceived(state.savedUsername, state.savedPassword)) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = Color.White,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = Color.White
                    ),
                    shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text("Login as ${state.savedUsername}", style = MaterialTheme.typography.titleMedium)
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
                        settings.userAgentString = com.example.filman.data.local.SessionManager(ctx).getUserAgent()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val cookies =
                                    CookieManager.getInstance().getCookie("https://filman.cc")
                                if (cookies != null && cookies.contains("PHPSESSID")) {
                                    if (url?.removeSuffix("/") == "https://filman.cc" || url?.contains(
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
                        loadUrl("https://filman.cc/logowanie")
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
