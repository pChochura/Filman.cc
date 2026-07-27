package com.example.filman.ui.login

import androidx.compose.runtime.Immutable
import com.example.filman.config.FilmanConfig
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared

internal sealed interface LoginEvent : FilmanEvent {
    // Define events here
}

@Immutable
internal data class LoginState(
    override val shared: SharedState = SharedState(),
    val backgroundImages: List<String> = emptyList(),
) : StateWithShared<LoginState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface LoginEffect {
    // Define effects here
}

internal class LoginViewModel(
    private val scraper: FilmanScraper,
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
            else -> {}
        }
    }
}
