package com.pointlessapps.filman

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.actor.ActorScreen
import com.pointlessapps.filman.ui.components.TiwiNavigationBar
import com.pointlessapps.filman.ui.components.TiwiNavigationItem
import com.pointlessapps.filman.ui.details.MovieDetailsScreen
import com.pointlessapps.filman.ui.favorites.FavoritesScreen
import com.pointlessapps.filman.ui.forkids.ForKidsScreen
import com.pointlessapps.filman.ui.home.HomeScreen
import com.pointlessapps.filman.ui.login.LoginScreen
import com.pointlessapps.filman.ui.movies.MoviesScreen
import com.pointlessapps.filman.ui.player.PlayerScreen
import com.pointlessapps.filman.ui.search.SearchScreen
import com.pointlessapps.filman.ui.tvshows.TvShowsScreen
import com.pointlessapps.filman.ui.watchhistory.WatchHistoryScreen

@Composable
internal fun AppNavigationBar(
    currentRoute: Route?,
    onRouteChanged: (Route) -> Unit,
    onScrollToTopRequested: () -> Unit,
    onBackClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    contentFocusRequester: FocusRequester,
) {
    if (currentRoute?.showNavigationBar == true || currentRoute?.showBackButton == true) {
        TiwiNavigationBar(
            currentRouteProvider = { currentRoute },
            onRouteChanged = onRouteChanged,
            onScrollToTopRequested = onScrollToTopRequested,
            items =
                if (currentRoute.showBackButton) {
                    listOf(TiwiNavigationItem.Back)
                } else {
                    listOf(
                        TiwiNavigationItem.Icon(
                            icon = R.drawable.ic_search,
                            contentDescription = R.string.common_search,
                            route = Route.Search,
                        ),
                        TiwiNavigationItem.Text(
                            title = R.string.common_tab_home,
                            route = Route.Home,
                        ),
                        TiwiNavigationItem.Text(
                            title = R.string.common_tab_movies,
                            route = Route.Movies,
                        ),
                        TiwiNavigationItem.Text(
                            title = R.string.common_tab_series,
                            route = Route.TvShows,
                        ),
                        TiwiNavigationItem.Text(
                            title = R.string.common_tab_kids,
                            route = Route.ForKids,
                        ),
                    )
                },
            onItemClicked = {
                when {
                    it === TiwiNavigationItem.Back -> onBackClicked()
                    it === TiwiNavigationItem.Settings -> onSettingsClicked()
                    else -> runCatching { contentFocusRequester.requestFocus() }
                }
            },
            contentFocusRequester = contentFocusRequester,
            showSettingsItem = !currentRoute.showBackButton,
        )
    }
}

@Composable
internal fun AppContent(
    backStack: List<Any>,
    transitionFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onNavigateTo: (Route?) -> Unit,
    paddingValues: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .size(1.dp)
                    .focusRequester(transitionFocusRequester)
                    .focusable(),
        )

        NavDisplay(
            backStack = backStack,
            onBack = { onNavigateTo(null) },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<Route.Login> { route ->
                        LoginScreen(
                            returnRoute = route.returnRoute,
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                    entry<Route.Home> {
                        HomeScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Search> {
                        SearchScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Movies> {
                        MoviesScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.TvShows> {
                        TvShowsScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.ForKids> {
                        ForKidsScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.WatchHistory> {
                        WatchHistoryScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Favorites> {
                        FavoritesScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Details> { route ->
                        MovieDetailsScreen(
                            request = route.request,
                            autoPlay = route.autoPlay,
                            episodeUrl = route.episodeUrl,
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Actor> { route ->
                        ActorScreen(
                            actorUrl = route.url,
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Player> { route ->
                        PlayerScreen(
                            url = route.url,
                            title = route.title,
                            description = route.description,
                            isTrailer = route.isTrailer,
                            onNavigateTo = onNavigateTo,
                        )
                    }
                },
        )
    }
}
