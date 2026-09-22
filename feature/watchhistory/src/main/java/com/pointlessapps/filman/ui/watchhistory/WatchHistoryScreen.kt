package com.pointlessapps.filman.ui.watchhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.summarySection
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun WatchHistoryScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: WatchHistoryViewModel = koinViewModel(),
) {
    val groupedItems by viewModel.groupedItems.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val returnFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    var lastFocusedItemIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                if (lastFocusedItemIds.isNotEmpty()) {
                    returnFocusRequester.requestFocus()
                    lastFocusedItemIds = lastFocusedItemIds.dropLast(1)
                } else if (groupedItems.isNotEmpty()) {
                    firstItemFocusRequester.requestFocus()
                } else {
                    contentFocusRequester.requestFocus()
                }
            }
        }
        onPauseOrDispose { }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is WatchHistoryEffect.NavigateToAuth -> onNavigateTo(Route.Login())
            is WatchHistoryEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.request))
        }
    }

    val focusRestorationState = FocusRestorationState(
        focusRequester = returnFocusRequester,
        lastFocusedItemKeys = lastFocusedItemIds,
    )

    CompositionLocalProvider(LocalFocusRestorationState provides focusRestorationState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = listState,
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + MaterialTheme.spacing.extraLarge,
                bottom = paddingValues.calculateBottomPadding() + MaterialTheme.spacing.extraLarge,
                start = MaterialTheme.spacing.extraLarge,
                end = MaterialTheme.spacing.extraLarge,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester),
        ) {
            val progressMap = state.shared.progressMap

            summary?.let { summary ->
                summarySection(
                    summary = summary,
                    firstItemFocusRequester = firstItemFocusRequester,
                )
            }

            groupedItems.forEachIndexed { index, section ->
                moviesGridSection(
                    title = section.title,
                    items = section.items,
                    isLoadingNextPage = false,
                    onItemClicked = { item ->
                        val url = item.movieItem.url
                        lastFocusedItemIds = lastFocusedItemIds + "${RECOMMENDED.prefix}$url"
                        viewModel.onEvent(BaseEvent.OpenMovieDetails(url = url))
                    },
                    onItemLongClicked = { item ->
                        viewModel.onEvent(BaseEvent.OpenContextMenu(movie = item.movieItem))
                    },
                    onLoadNextPageRequest = { },
                    showLoadMoreButton = false,
                    onShowMoreClicked = { },
                    firstItemFocusRequester = if (index == 0 && summary == null) firstItemFocusRequester else null,
                    leftItemFocusRequester = null,
                    progressProvider = { progressMap },
                )
            }
        }
    }

    state.shared.overlayMenuData?.let { data ->
        FilmanOverlayMenu(
            title = data.title,
            items = data.items,
            onDismissRequest = { viewModel.onEvent(BaseEvent.CloseContextMenu) },
        )
    }
}
