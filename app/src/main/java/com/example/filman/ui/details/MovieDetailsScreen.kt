package com.example.filman.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.example.filman.Route
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.FilmanScaffold
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.details.sections.posterSection
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun MovieDetailsRoute(
    movieUrl: String,
    onNavigateTo: (Route) -> Unit,
    viewModel: MovieDetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(movieUrl) {
        viewModel.onEvent(MovieDetailsEvent.LoadDetails(movieUrl))
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is MovieDetailsEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is MovieDetailsEffect.NavigateToPlayer -> onNavigateTo(Route.Player(effect.url))
        }
    }

    FilmanScaffold(
        navigationTopBar = {},
    ) { paddingValues ->
        AnimatedContent(
            targetState = state.isLoading,
            contentAlignment = Alignment.Center,
        ) { isLoading ->
            if (isLoading) {
                FilmanFullscreenLoader()
            } else {
                MovieDetailsContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    paddingValues = paddingValues,
                )
            }
        }
    }

//    state.overlayMenuData?.let { data ->
//        FilmanOverlayMenu(
//            title = data.title,
//            items = data.items,
//            onDismissRequest = { viewModel.onEvent(HomeEvent.CloseContextMenu) },
//        )
//    }
}

@Composable
private fun MovieDetailsContent(
    state: MovieDetailsState,
    onEvent: (MovieDetailsEvent) -> Unit,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        contentPadding = PaddingValues(
            bottom = MaterialTheme.spacing.extraLarge,
        ),
    ) {
        if (state.mediaDetails != null) {
            posterSection(state.mediaDetails)
        }
    }
}
