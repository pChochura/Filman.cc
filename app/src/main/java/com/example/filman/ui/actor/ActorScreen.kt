package com.example.filman.ui.actor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.example.filman.Route
import com.example.filman.ui.base.BaseEvent
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.sections.actorInfoSection
import com.example.filman.ui.components.sections.moviesGridSection
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.FocusRestorationState
import com.example.filman.ui.core.LocalFocusRestorationState
import com.example.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ActorScreen(
    actorUrl: String,
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    viewModel: ActorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    LaunchedEffect(actorUrl) {
        viewModel.onEvent(ActorEvent.LoadDetails(actorUrl))
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is ActorEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is ActorEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
        }
    }

    LifecycleResumeEffect(Unit) {
        coroutineScope.launch {
            delay(100.milliseconds)
            runCatching {
                if (lastFocusedItemId != null) {
                    returnFocusRequester.requestFocus()
                } else {
                    contentFocusRequester.requestFocus()
                }
            }
        }

        onPauseOrDispose { }
    }

    val isActorInfoSectionVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < 50
        }
    }
    BackHandler(!isActorInfoSectionVisible) {
        coroutineScope.launch {
            if (listState.firstVisibleItemIndex > 0) {
                listState.scrollToItem(1)
            }
            listState.animateScrollToItem(0)
            contentFocusRequester.requestFocus()
        }
    }

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            ActorContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                onItemClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url))
                },
                focusRestorationState = FocusRestorationState(
                    focusRequester = returnFocusRequester,
                    lastFocusedItemKey = lastFocusedItemId,
                ),
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

@Composable
private fun ActorContent(
    state: ActorState,
    listState: LazyGridState,
    onEvent: (FilmanEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    onItemClicked: (sectionPrefix: String, url: String) -> Unit,
    focusRestorationState: FocusRestorationState,
) {
    val resources = LocalResources.current

    CompositionLocalProvider(LocalFocusRestorationState provides focusRestorationState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = listState,
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge)
                .plus(PaddingValues(bottom = MaterialTheme.spacing.extraLarge)),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester),
        ) {
            actorInfoSection(
                actorDetails = state.actorDetails,
            )

            state.moviesSections.forEach { section ->
                moviesGridSection(
                    title = resources.getString(section.title),
                    items = section.movies,
                    isLoadingNextPage = state.isLoadingNextPage,
                    onItemClicked = { onItemClicked(RECOMMENDED.prefix, it.url) },
                    onItemLongClicked = { item ->
                        onEvent(
                            BaseEvent.OpenContextMenu(
                                title = item.titlePl,
                                url = item.url,
                                posterUrl = item.posterUrl,
                            ),
                        )
                    },
                    onLoadNextPageRequest = { },
                    showLoadMoreButton = false,
                    onShowMoreClicked = { },
                    firstItemFocusRequester = null,
                )
            }
        }
    }
}
