package com.pointlessapps.filman.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.ContextMenuOption
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.MediaCard
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.components.sections.episodesRowSection
import com.pointlessapps.filman.ui.components.sections.errorSection
import com.pointlessapps.filman.ui.components.sections.movieDetailsSection
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.posterSection
import com.pointlessapps.filman.ui.components.sections.tabRowSection
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.CREW
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.EPISODES
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MovieDetailsScreen(
    request: DetailsRequest,
    autoPlay: Boolean = false,
    episodeUrl: String? = null,
    onNavigateTo: (Route?) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: MovieDetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyGridState()

    LaunchedEffect(request) {
        viewModel.onEvent(MovieDetailsEvent.LoadDetails(request))
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is MovieDetailsEffect.NavigateToAuth -> onNavigateTo(Route.Login())
            is MovieDetailsEffect.NavigateBack -> onNavigateTo(null)
            is MovieDetailsEffect.NavigateToPlayer -> onNavigateTo(Route.Player(effect.url))
            is MovieDetailsEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.request))
            is MovieDetailsEffect.NavigateToActor -> onNavigateTo(Route.Actor(effect.url))
            is MovieDetailsEffect.ShowToast -> toastMessage = effect.message.asString(context)
        }
    }

    var hasAutoPlayed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (autoPlay && !hasAutoPlayed && !state.isLoading && state.mediaDetails != null) {
            hasAutoPlayed = true
            val url = episodeUrl ?: state.watchButtonState.url
            if (url.isNotEmpty()) {
                viewModel.onEvent(MovieDetailsEvent.PlayItem(url))
            }
        }
    }

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                if (lastFocusedItemIds.isNotEmpty()) {
                    returnFocusRequester.requestFocus()
                    lastFocusedItemIds = lastFocusedItemIds.dropLast(1)
                } else {
                    contentFocusRequester.requestFocus()
                }
            }
        }

        onPauseOrDispose { }
    }

    val isPosterSectionVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < 50
        }
    }
    BackHandler(!isPosterSectionVisible) {
        coroutineScope.launch {
            if (listState.firstVisibleItemIndex > 0) {
                listState.scrollToItem(1)
            }
            listState.animateScrollToItem(0)
            contentFocusRequester.requestFocus()
        }
    }

    state.searchResultsChoice?.let { searchResults ->
        Dialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { viewModel.onEvent(MovieDetailsEvent.CancelSearchResultSelection) },
        ) {
            Column(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.common_results),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.details_results_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(MaterialTheme.spacing.small))

                LazyRow(
                    contentPadding = PaddingValues(
                        horizontal = MaterialTheme.spacing.small,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    items(searchResults) { searchResult ->
                        MediaCard(
                            title = searchResult.titlePl + if (searchResult.seasonNumber != null) {
                                " (S${searchResult.seasonNumber})"
                            } else {
                                ""
                            },
                            posterUrl = searchResult.posterUrl,
                            aspectRatio = 0.75f,
                            width = 120.dp,
                            onItemClicked = {
                                viewModel.onEvent(
                                    MovieDetailsEvent.SelectSearchResult(searchResult.url),
                                )
                            },
                            onItemLongClicked = {},
                        )
                    }
                }
            }
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            MovieDetailsContent(
                state = state,
                listState = listState,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onMovieClicked = { sectionPrefix, movieItem ->
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix${movieItem.url}"
                    viewModel.onEvent(
                        BaseEvent.OpenMovieDetails(
                            url = movieItem.url,
                            request = movieItem.detailsRequest,
                        ),
                    )
                },
                onPlayItem = { sectionPrefix, url ->
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix$url"
                    viewModel.onEvent(MovieDetailsEvent.PlayItem(url))
                },
                onActorClicked = { sectionPrefix, url ->
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix$url"
                    viewModel.onEvent(MovieDetailsEvent.OpenActorDetails(url))
                },
                onTabSelected = { viewModel.onEvent(MovieDetailsEvent.TabChanged(it)) },
                onOpenContextMenu = { movie, options ->
                    viewModel.onEvent(BaseEvent.OpenContextMenu(movie, options))
                },
                onWatchClicked = { sectionPrefix, url ->
                    lastFocusedItemIds = lastFocusedItemIds + "${sectionPrefix}watch_button"
                    viewModel.onEvent(MovieDetailsEvent.PlayItem(url))
                },
                onWatchTrailerClicked = { url ->
                    viewModel.onEvent(MovieDetailsEvent.WatchTrailer(url))
                },
                focusRestorationState =
                    FocusRestorationState(
                        focusRequester = returnFocusRequester,
                        lastFocusedItemKeys = lastFocusedItemIds,
                    ),
                onRefresh = { viewModel.onEvent(MovieDetailsEvent.LoadDetails(request)) },
                onLoadMoreRecommendations = { viewModel.onEvent(MovieDetailsEvent.LoadMoreRecommendations) },
            )
        }
    }

    state.overlayMenuData?.let { data ->
        FilmanOverlayMenu(
            title = data.title,
            items = data.items,
            onDismissRequest = { viewModel.onEvent(BaseEvent.CloseContextMenu) },
        )
    }
}

