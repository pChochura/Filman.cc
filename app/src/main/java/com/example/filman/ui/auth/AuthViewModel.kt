package com.example.filman.ui.auth

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.SessionManager
import com.example.filman.data.server.CredentialServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthEvent {
    data class OnIpResolved(val ip: String) : AuthEvent
    data class OnCookieReceived(val cookie: String) : AuthEvent
    data class OnCredentialsReceived(val user: String, val pass: String) : AuthEvent
    data object OnAuthSuccess : AuthEvent
}

@Immutable
data class AuthState(
    val localIp: String = "Starting server...",
    val qrCodeBitmap: Bitmap? = null,
    val receivedUsername: String? = null,
    val receivedPassword: String? = null,
    val savedUsername: String? = null,
    val savedPassword: String? = null,
)

sealed interface AuthEffect {
    data object NavigateToHome : AuthEffect
    data class InjectCredentials(val user: String, val pass: String) : AuthEffect
}

class AuthViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AuthState(
            savedUsername = sessionManager.getSavedUsername(),
            savedPassword = sessionManager.getSavedPassword(),
        )
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var server: CredentialServer? = null

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.OnIpResolved -> {
                _state.update { it.copy(localIp = event.ip) }
                generateQrCode(event.ip)
                startServer()
            }

            is AuthEvent.OnCookieReceived -> {
                var cleanCookie = event.cookie.trim()
                if (cleanCookie.startsWith("Cookie:", ignoreCase = true)) {
                    cleanCookie = cleanCookie.substring(7).trim()
                }
                sessionManager.saveCookie(cleanCookie)

                _effect.trySend(AuthEffect.NavigateToHome)
            }

            is AuthEvent.OnCredentialsReceived -> {
                _state.update {
                    it.copy(
                        receivedUsername = event.user,
                        receivedPassword = event.pass,
                    )
                }
                sessionManager.saveCredentials(event.user, event.pass)
                _effect.trySend(AuthEffect.InjectCredentials(event.user, event.pass))
            }

            AuthEvent.OnAuthSuccess -> {
                _effect.trySend(AuthEffect.NavigateToHome)
            }
        }
    }

    private fun generateQrCode(url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val writer = QRCodeWriter()
            runCatching {
                val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp[x, y] = if (bitMatrix.get(x, y)) {
                            Color.BLACK
                        } else {
                            Color.WHITE
                        }
                    }
                }
                _state.update { it.copy(qrCodeBitmap = bmp) }
            }
        }
    }

    private fun startServer() {
        if (server != null) return
        server = CredentialServer(
            port = 8080,
            onCookieReceived = { cookie ->
                onEvent(AuthEvent.OnCookieReceived(cookie))
            },
            onCredentialsReceived = { user, pass ->
                onEvent(AuthEvent.OnCredentialsReceived(user, pass))
            },
        )
        runCatching { server?.start() }
    }

    override fun onCleared() {
        runCatching { server?.stop() }
        super.onCleared()
    }
}
