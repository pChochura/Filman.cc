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
    data class OnCookieReceived(val cookie: String) : LoginEvent
    data object OnLoginClicked : LoginEvent
    data object OnAuthSuccess : LoginEvent
}

@Immutable
internal data class LoginState(
    override val shared: SharedState = SharedState(),
    val backgroundImages: List<String> = emptyList(),
    val isLoginLoading: Boolean = false,
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
            }

            is LoginEvent.OnAuthSuccess -> sendEffect(LoginEffect.NavigateBack)
            is LoginEvent.OnLoginClicked -> updateState {
                it.copy(isLoginLoading = true)
            }
        }
    }
}
