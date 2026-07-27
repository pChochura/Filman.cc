package com.example.filman.ui.login

import androidx.compose.runtime.Immutable
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
) : StateWithShared<LoginState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface LoginEffect {
    // Define effects here
}

internal class LoginViewModel : BaseViewModel<LoginState, LoginEvent, LoginEffect>(
    initialState = LoginState(),
) {
    override fun getAuthErrorEffect() = null

    override fun handleEvent(event: LoginEvent) {
        when (event) {
            else -> {}
        }
    }
}
