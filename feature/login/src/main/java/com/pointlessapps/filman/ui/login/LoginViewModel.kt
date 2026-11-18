package com.pointlessapps.filman.ui.login

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncAuthRequest
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.core.TextValue

sealed interface LoginEvent : FilmanEvent {
    data class OnLoginClicked(
        val username: String,
        val pass: String,
        val isRegister: Boolean = false,
    ) : LoginEvent

    data class OnModeChanged(
        val isRegisterMode: Boolean,
    ) : LoginEvent

    data object OnDismissError : LoginEvent
}

@Immutable
data class LoginState(
    override val shared: SharedState = SharedState(),
    val backgroundImages: List<String> = emptyList(),
    val isLoginLoading: Boolean = false,
    val isRegisterMode: Boolean = false,
    val authErrorMessage: TextValue? = null,
) : StateWithShared<LoginState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface LoginEffect {
    data object NavigateBack : LoginEffect
}

class LoginViewModel(
    private val repository: FilmanRepository,
    private val sessionManager: SessionManager,
    private val syncApiClient: SyncApiClient,
    private val syncManager: SyncManager,
) : BaseViewModel<LoginState, LoginEvent, LoginEffect>(
    initialState = LoginState(),
) {

    init {
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

    override fun getAuthErrorEffect() = null

    override fun handleEvent(event: LoginEvent) {
        when (event) {
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
