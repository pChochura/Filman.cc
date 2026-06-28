package com.example.filman.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.example.filman.data.local.SessionManager
import com.example.filman.data.server.CredentialServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(
    sessionManager: SessionManager,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var localIp by remember { mutableStateOf("Starting server...") }
    var receivedUsername by remember { mutableStateOf<String?>(null) }
    var receivedPassword by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // Virtual cursor state
    var cursorX by remember { mutableStateOf(400f) }
    var cursorY by remember { mutableStateOf(300f) }
    var boxWidth by remember { mutableStateOf(1000f) }
    var boxHeight by remember { mutableStateOf(800f) }
    val focusRequester = remember { FocusRequester() }
    
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        val ip = String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
        
        val port = 8080
        val url = "http://$ip:$port"
        localIp = url

        // Generate QR Code
        scope.launch(Dispatchers.Default) {
            val writer = QRCodeWriter()
            try {
                val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                    }
                }
                qrCodeBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Start Server
        val server = CredentialServer(
            port = port,
            onCookieReceived = { cookie ->
                var cleanCookie = cookie.trim()
                if (cleanCookie.startsWith("Cookie:", ignoreCase = true)) {
                    cleanCookie = cleanCookie.substring(7).trim()
                }
                val phpsessid = cleanCookie.split(";").map { it.trim() }
                    .firstOrNull { it.startsWith("PHPSESSID=") }
                
                if (phpsessid != null) {
                    sessionManager.saveCookie(phpsessid)
                } else {
                    sessionManager.saveCookie(cleanCookie)
                }
                
                scope.launch(Dispatchers.Main) { onAuthSuccess() }
            },
            onCredentialsReceived = { user, pass ->
                scope.launch(Dispatchers.Main) {
                    receivedUsername = user
                    receivedPassword = pass
                    // Inject credentials into WebView
                    webViewRef?.evaluateJavascript(
                        "document.querySelector('input[name=\"login\"]').value = '$user';" +
                        "document.querySelector('input[name=\"password\"]').value = '$pass';",
                        null
                    )
                }
            }
        )
        try {
            server.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        onDispose {
            try {
                server.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        // Request focus to intercept D-Pad
        focusRequester.requestFocus()
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Left Side: Instructions and QR Code
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Filman.cc Setup", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Scan to send cookie or credentials:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            
            qrCodeBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(200.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(localIp, style = MaterialTheme.typography.labelLarge)
            
            Spacer(modifier = Modifier.height(32.dp))
            if (receivedUsername != null) {
                Text("Credentials received! Please complete the reCAPTCHA on the right.", color = Color.Green)
            }
        }

        // Right Side: WebView with Virtual Cursor
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(2f)
                .padding(16.dp)
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
                                // Simulate Click on WebView
                                val downTime = SystemClock.uptimeMillis()
                                val motionEventDown = MotionEvent.obtain(
                                    downTime, 
                                    downTime, 
                                    MotionEvent.ACTION_DOWN, 
                                    cursorX, 
                                    cursorY, 
                                    0
                                )
                                webViewRef?.dispatchTouchEvent(motionEventDown)
                                
                                val motionEventUp = MotionEvent.obtain(
                                    downTime, 
                                    SystemClock.uptimeMillis(), 
                                    MotionEvent.ACTION_UP, 
                                    cursorX, 
                                    cursorY, 
                                    0
                                )
                                webViewRef?.dispatchTouchEvent(motionEventUp)
                                true
                            }
                            else -> false
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val cookies = CookieManager.getInstance().getCookie("https://filman.cc")
                                if (cookies != null && cookies.contains("PHPSESSID")) {
                                    if (url?.removeSuffix("/") == "https://filman.cc" || url?.contains("profile") == true || url?.contains("konto") == true) {
                                        sessionManager.saveCookie(cookies)
                                        onAuthSuccess()
                                    }
                                }
                            }
                        }
                        webViewRef = this
                        loadUrl("https://filman.cc/logowanie")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Virtual Cursor Pointer
            Box(
                modifier = Modifier
                    .offset { IntOffset(cursorX.roundToInt(), cursorY.roundToInt()) }
                    .size(16.dp)
                    .background(Color.Red, CircleShape)
            )
        }
    }
}
