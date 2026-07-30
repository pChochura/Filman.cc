package com.example.filman.ui.login

import androidx.compose.runtime.Immutable
import com.example.filman.config.FilmanConfig
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared

internal sealed interface LoginEvent : FilmanEvent {
    data class OnCookieReceived(val cookie: String, val userAgent: String) : LoginEvent
    data class OnLoginClicked(val username: String, val pass: String) : LoginEvent
    data object OnAuthSuccess : LoginEvent
    data object OnLoginFailed : LoginEvent
}

@Immutable
internal data class LoginState(
    override val shared: SharedState = SharedState(),
    val backgroundImages: List<String> = emptyList(),
    val isLoginLoading: Boolean = false,
    val savedUsername: String? = null,
    val savedPassword: String? = null,
) : StateWithShared<LoginState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface LoginEffect {
    data object NavigateBack : LoginEffect
}

internal class LoginViewModel(
    private val scraper: FilmanScraper,
    private val sessionManager: SessionManager,
) : BaseViewModel<LoginState, LoginEvent, LoginEffect>(
    initialState = LoginState(),
) {

    private var pendingUsername = ""
    private var pendingPassword = ""

    init {
        launchHandled {
            updateSharedState { it.copy(isLoading = true) }

            val homePage = scraper.getCategoryPage(FilmanConfig.PATH_HOME)
            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false),
                    backgroundImages = homePage.featuredItems
                        .mapNotNull(MovieItem::backgroundUrl),
                )
            }
        }
        launchHandled {
            sessionManager.usernameFlow.collect { user ->
                updateState { it.copy(savedUsername = user) }
            }
        }
        launchHandled {
            sessionManager.passwordFlow.collect { pass ->
                updateState { it.copy(savedPassword = pass) }
            }
        }
    }

    override fun getAuthErrorEffect() = null

    override fun handleEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.OnCookieReceived -> {
                var cleanCookie = event.cookie.trim()
                if (cleanCookie.startsWith("Cookie:", ignoreCase = true)) {
                    cleanCookie = cleanCookie.substringAfter("Cookie:").trim()
                }

                sessionManager.saveCookie(cleanCookie)
                if (event.userAgent.isNotBlank()) {
                    sessionManager.saveUserAgent(event.userAgent)
                }
            }

            is LoginEvent.OnAuthSuccess -> {
                if (pendingUsername.isNotBlank() && pendingPassword.isNotBlank()) {
                    sessionManager.saveCredentials(pendingUsername, pendingPassword)
                }
                sendEffect(LoginEffect.NavigateBack)
            }

            is LoginEvent.OnLoginClicked -> {
                pendingUsername = event.username
                pendingPassword = event.pass
                updateState { it.copy(isLoginLoading = true) }
            }

            is LoginEvent.OnLoginFailed -> updateState {
                it.copy(isLoginLoading = false)
            }
        }
    }
}
