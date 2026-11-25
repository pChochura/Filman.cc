package com.pointlessapps.filman.ui.login

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncAuthRequest
import com.pointlessapps.filman.data.sync.model.SyncUser
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LoginEvent : TiwiEvent {
    data class OnLoginClicked(
        val username: String,
        val pass: String,
        val isRegister: Boolean = false,
    ) : LoginEvent

    data class OnModeChanged(
        val isRegisterMode: Boolean,
    ) : LoginEvent

    data object OnDismissError : LoginEvent

    data object OnRefreshPairingCode : LoginEvent
}

@Immutable
data class LoginState(
    override val shared: SharedState = SharedState(),
    val backgroundImages: List<String> = emptyList(),
    val isLoginLoading: Boolean = false,
    val isRegisterMode: Boolean = false,
    val authErrorMessage: TextValue? = null,
    val pairingCode: String? = null,
    val rawPairingCode: String? = null,
    val pairingUrl: String? = null,
    val pairingDomain: String = "tiwi-player.duckdns.org",
    val isPairingLoading: Boolean = false,
    val isPairingExpired: Boolean = false,
    val pairingExpiresAt: Long = 0L,
) : StateWithShared<LoginState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface LoginEffect {
    data object NavigateBack : LoginEffect
}

class LoginViewModel(
    private val repository: TiwiRepository,
    private val sessionManager: SessionManager,
    private val syncApiClient: SyncApiClient,
    private val syncManager: SyncManager,
) : BaseViewModel<LoginState, LoginEvent, LoginEffect>(
    initialState = LoginState(),
) {

    private var pairingJob: Job? = null

    init {
        startDevicePairing()

        launchHandled {
            updateSharedState { it.copy(isLoading = true) }

            val homePage = repository.getFeedPage(TmdbFeed.TrendingAll())
            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false),
                    backgroundImages =
                        homePage.featuredItems
                            .mapNotNull(MovieItem::backgroundUrl),
                )
            }
        }
    }

    private fun startDevicePairing() {
        pairingJob?.cancel()
        pairingJob = launchHandled {
            updateState {
                it.copy(
                    isPairingLoading = true,
                    isPairingExpired = false,
                )
            }

            val pairRes = syncApiClient.requestDevicePairCode()
            pairRes.fold(
                onSuccess = { response ->
                    val expiresAt = System.currentTimeMillis() + response.expiresIn * 1000L
                    val domain = response.verificationDomain.ifBlank { "tiwi-player.duckdns.org" }
                    val verificationUrl = if (response.verificationUrlComplete.startsWith("http")) {
                        response.verificationUrlComplete
                    } else {
                        "https://$domain/pair?code=${response.rawUserCode}"
                    }

                    updateState {
                        it.copy(
                            pairingCode = response.userCode,
                            rawPairingCode = response.rawUserCode,
                            pairingUrl = verificationUrl,
                            pairingDomain = domain,
                            pairingExpiresAt = expiresAt,
                            isPairingLoading = false,
                            isPairingExpired = false,
                        )
                    }

                    launchDevicePairListeners(response.deviceCode, expiresAt)
                },
                onFailure = {
                    updateState {
                        it.copy(
                            isPairingLoading = false,
                        )
                    }
                },
            )
        }
    }

    private fun CoroutineScope.launchDevicePairListeners(deviceCode: String, expiresAt: Long) {
        launch {
            runCatching {
                syncApiClient.listenDevicePairEvents(deviceCode) { token, user ->
                    handlePairingAuthorized(token, user)
                }
            }
        }

        launch {
            val timeoutMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(1000L)
            withTimeoutOrNull(timeoutMs) {
                while (isActive) {
                    delay(2000L)
                    val statusRes = syncApiClient.checkDevicePairStatus(deviceCode)
                    val status = statusRes.getOrNull()
                    val token = status?.token
                    if (status != null && status.status == "authorized" && !token.isNullOrBlank()) {
                        handlePairingAuthorized(token, status.user)
                        return@withTimeoutOrNull
                    }
                }
            }
            updateState { it.copy(isPairingExpired = true) }
        }
    }

    private fun handlePairingAuthorized(token: String, user: SyncUser?) {
        sessionManager.saveAuthToken(
            token = token,
            userId = user?.id,
            username = user?.username,
        )
        syncManager.triggerSync()
        sendEffect(LoginEffect.NavigateBack)
    }

    override fun getAuthErrorEffect() = null

    override fun handleEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.OnRefreshPairingCode -> {
                startDevicePairing()
            }

            is LoginEvent.OnModeChanged -> {
                updateState {
                    it.copy(
                        isRegisterMode = event.isRegisterMode,
                        authErrorMessage = null,
                    )
                }
            }

            is LoginEvent.OnDismissError -> {
                updateState { it.copy(authErrorMessage = null) }
            }

            is LoginEvent.OnLoginClicked -> {
                val cleanUser = event.username.trim()
                val cleanPass = event.pass.trim()

                if (cleanUser.isBlank() || cleanPass.isBlank()) {
                    updateState {
                        it.copy(
                            authErrorMessage = TextValue.StringResource(R.string.login_error_invalid_credentials),
                        )
                    }
                    return
                }

                updateState {
                    it.copy(
                        isLoginLoading = true,
                        authErrorMessage = null,
                    )
                }

                launchHandled {
                    val request = SyncAuthRequest(username = cleanUser, password = cleanPass)
                    val result = if (event.isRegister) {
                        syncApiClient.register(request)
                    } else {
                        syncApiClient.login(request)
                    }

                    result.fold(
                        onSuccess = { response ->
                            val token = response.token ?: "session_${System.currentTimeMillis()}"
                            sessionManager.saveAuthToken(
                                token = token,
                                userId = response.user?.id,
                                username = cleanUser,
                            )
                            syncManager.triggerSync()

                            updateState { it.copy(isLoginLoading = false) }
                            sendEffect(LoginEffect.NavigateBack)
                        },
                        onFailure = { error ->
                            val message = error.message
                            val errorTextValue = if (message != null && message.isNotBlank()) {
                                TextValue.DynamicString(message)
                            } else {
                                TextValue.StringResource(R.string.login_error_server)
                            }
                            updateState {
                                it.copy(
                                    isLoginLoading = false,
                                    authErrorMessage = errorTextValue,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
