package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.ui.components.TiwiButton
import com.pointlessapps.filman.ui.components.TiwiIconButton
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.sectionFocusRestorer
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.launch

fun LazyGridScope.posterSection(
    detailedMedia: DetailedMedia?,
    watchButtonText: String,
    isWatchButtonEnabled: Boolean,
    trailerUrl: String?,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onMoreOptionsClicked: () -> Unit,
    paddingValues: PaddingValues,
) {
    if (detailedMedia == null) return

    item(
        key = "poster_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "PosterSectionContent",
    ) {
        PosterSectionContent(
            detailedMedia = detailedMedia,
            watchButtonText = watchButtonText,
            isWatchButtonEnabled = isWatchButtonEnabled,
            trailerUrl = trailerUrl,
            onWatchClicked = onWatchClicked,
            onWatchTrailerClicked = onWatchTrailerClicked,
            onMoreOptionsClicked = onMoreOptionsClicked,
            paddingValues = paddingValues,
        )
    }
}

@Composable
private fun PosterSectionContent(
    detailedMedia: DetailedMedia,
    watchButtonText: String,
    isWatchButtonEnabled: Boolean,
    trailerUrl: String?,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onMoreOptionsClicked: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
) {
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val watchButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier =
            modifier
                .horizontalBleed(MaterialTheme.spacing.extraLarge)
                .fillMaxWidth()
                .height(LocalWindowInfo.current.containerDpSize.height * 0.9f)
                .bringIntoViewRequester(bringIntoViewRequester)
                .focusGroup()
                .sectionFocusRestorer(
                    sectionKeyPrefix = FEATURED.prefix,
                    defaultFallback = watchButtonFocusRequester,
                )
                .onFocusChanged {
                    if (it.hasFocus) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(detailedMedia.baseItem.backgroundUrl)
                    .size(600)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(0.5f)
                    .gradientForeground(),
        )

        PosterSectionInfo(
            detailedMedia = detailedMedia,
            watchButtonText = watchButtonText,
            isWatchButtonEnabled = isWatchButtonEnabled,
            trailerUrl = trailerUrl,
            watchButtonFocusRequester = watchButtonFocusRequester,
            onWatchClicked = onWatchClicked,
            onWatchTrailerClicked = onWatchTrailerClicked,
            onMoreOptionsClicked = onMoreOptionsClicked,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(top = paddingValues.calculateTopPadding()),
        )
    }
}

@Composable
private fun PosterSectionInfo(
    detailedMedia: DetailedMedia,
    watchButtonText: String,
    isWatchButtonEnabled: Boolean,
    trailerUrl: String?,
    watchButtonFocusRequester: FocusRequester,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onMoreOptionsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(vertical = MaterialTheme.spacing.large)
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .fillMaxWidth(0.6f),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = detailedMedia.baseItem.titlePl,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        PosterSectionMetaInfo(
            imdbRating = detailedMedia.baseItem.imdbRating,
            tiwiRating = detailedMedia.baseItem.tiwiRating ?: detailedMedia.baseItem.filmanRating,
            seasonsNumber = detailedMedia.seasonsNumber,
            duration = detailedMedia.metaInfo?.duration,
            year = detailedMedia.metaInfo?.year,
            countries = detailedMedia.metaInfo?.countries.orEmpty(),
            categories = detailedMedia.categories.map { it.name },
        )

        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = detailedMedia.baseItem.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        PosterSectionCTA(
            watchButtonText = watchButtonText,
            isWatchButtonEnabled = isWatchButtonEnabled,
            trailerUrl = trailerUrl,
            watchButtonFocusRequester = watchButtonFocusRequester,
            onWatchClicked = onWatchClicked,
            onWatchTrailerClicked = onWatchTrailerClicked,
            onMoreOptionsClicked = onMoreOptionsClicked,
        )
    }
}

@Composable
private fun PosterSectionCTA(
    watchButtonText: String,
    isWatchButtonEnabled: Boolean,
    trailerUrl: String?,
    watchButtonFocusRequester: FocusRequester,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onMoreOptionsClicked: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .padding(top = MaterialTheme.spacing.medium)
                .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TiwiButton(
            text = watchButtonText,
            iconRes = R.drawable.ic_play,
            onClick = onWatchClicked,
            enabled = isWatchButtonEnabled,
            modifier =
                Modifier
                    .wrapContentWidth()
                    .focusRequester(watchButtonFocusRequester)
                    .withFocusRestoration("${FEATURED.prefix}watch_button")
                    .focusProperties { left = watchButtonFocusRequester },
        )

        TiwiButton(
            text = stringResource(R.string.common_watch_trailer),
            enabled = trailerUrl != null,
            iconRes = R.drawable.ic_trailer,
            onClick = { onWatchTrailerClicked(trailerUrl.orEmpty()) },
            modifier =
                Modifier
                    .wrapContentWidth()
                    .withFocusRestoration("${FEATURED.prefix}watch_trailer_button"),
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.onBackground,
        )

        TiwiIconButton(
            icon = R.drawable.ic_more_vert,
            contentDescription = R.string.common_more_options,
            onClick = onMoreOptionsClicked,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        )
    }
}
