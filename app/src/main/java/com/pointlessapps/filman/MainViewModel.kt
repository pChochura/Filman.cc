package com.pointlessapps.filman

import android.content.Context
import android.content.Intent
import android.webkit.WebSettings
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class MainViewModel(
    private val sessionManager: SessionManager,
    private val settingsManager: SettingsManager,
    private val progressManager: ProgressManager,
    private val searchHistoryManager: SearchHistoryManager,
    private val modelCache: ModelCache,
) : ViewModel() {

    val backStack = mutableStateListOf<Route>()

    val extractorsPriority = settingsManager.extractorsPriorityFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList(),
    )

    val preferredQuality = settingsManager.preferredQualityFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = "auto",
    )

    val autoPlayNextEpisode = settingsManager.autoPlayNextFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = true,
    )

    private val _showSettingsOverlay = MutableStateFlow(false)
    val showSettingsOverlay = _showSettingsOverlay.asStateFlow()

    val isLoggedIn = sessionManager.cookieFlow.map { !it.isNullOrEmpty() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = false,
    )

    fun setUserAgent(context: Context) {
        if (!sessionManager.hasCookie()) {
            sessionManager.saveUserAgent(WebSettings.getDefaultUserAgent(context))
        }
    }

    fun initBackStack(startDestination: Route) {
        if (backStack.isEmpty()) {
            backStack.add(startDestination)
        }
    }

    fun handleNavigateTo(route: Route?) {
        if (route == null) {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        } else {
            var routeToAdd = route
            if (routeToAdd is Route.Login) {
                val currentRoute = backStack.lastOrNull()
                if (currentRoute != null && currentRoute !is Route.Login) {
                    routeToAdd = routeToAdd.copy(
                        returnRoute = routeToAdd.returnRoute ?: currentRoute,
                    )
                    if (routeToAdd.replaceCurrentRoute) {
                        backStack.removeLastOrNull()
                    }
                }
            } else if (backStack.lastOrNull() is Route.Login) {
                val loginRoute = backStack.lastOrNull() as Route.Login
                backStack.removeLastOrNull()

                if (!loginRoute.replaceCurrentRoute && backStack.lastOrNull() == routeToAdd) {
                    return
                }
            }

            backStack.add(routeToAdd)
        }
    }

    fun navigateToTab(route: Route) {
        backStack.removeAll { it.showNavigationBar }
        backStack.add(route)
    }

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (
            data.scheme == FilmanConfig.DEEP_LINK_SCHEME &&
            data.host == FilmanConfig.DEEP_LINK_HOST_DETAILS
        ) {
            val url = data.getQueryParameter(FilmanConfig.DEEP_LINK_PARAM_URL)
            val episodeUrl = data.getQueryParameter(FilmanConfig.DEEP_LINK_PARAM_EPISODE_URL)
            val autoPlay = data.getQueryParameter("autoPlay") == "true"
            if (url != null) {
                backStack.add(
                    Route.Details(
                        url = url,
                        autoPlay = autoPlay,
                        episodeUrl = episodeUrl,
                    ),
                )
            }
        }
    }

    fun setShowSettingsOverlay(show: Boolean) {
        _showSettingsOverlay.update { show }
    }

    fun onLogoutClicked() {
        sessionManager.clearCookie()
        _showSettingsOverlay.update { false }
    }

    fun onMoveExtractorUp(index: Int) {
        val currentList = extractorsPriority.value.toMutableList()
        if (index > 0 && index < currentList.size) {
            val item = currentList.removeAt(index)
            currentList.add(index - 1, item)
            settingsManager.saveExtractorsPriority(currentList)
        }
    }

    fun onMoveExtractorDown(index: Int) {
        val currentList = extractorsPriority.value.toMutableList()
        if (index >= 0 && index < currentList.size - 1) {
            val item = currentList.removeAt(index)
            currentList.add(index + 1, item)
            settingsManager.saveExtractorsPriority(currentList)
        }
    }

    fun setPreferredQuality(quality: String) {
        settingsManager.setPreferredQuality(quality)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        settingsManager.setAutoPlayNext(enabled)
    }

    fun clearCache() {
        modelCache.clearAll()
    }

    fun clearWatchHistory() {
        progressManager.clearAll()
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearAll()
    }
}
