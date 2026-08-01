package com.pointlessapps.filman.ui.components.sections

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.components.FilmanIconButton
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.sectionFocusRestorer
import com.pointlessapps.filman.ui.core.titlecase
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.ImdbColor
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlin.time.Duration

internal fun LazyGridScope.posterSection(
    detailedMedia: DetailedMedia?,
    isFavourite: Boolean,
    watchButtonText: String,
    trailerUrl: String?,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onToggleFavouritesClicked: () -> Unit,
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
            isFavourite = isFavourite,
            watchButtonText = watchButtonText,
            trailerUrl = trailerUrl,
            onWatchClicked = onWatchClicked,
            onWatchTrailerClicked = onWatchTrailerClicked,
            onToggleFavouritesClicked = onToggleFavouritesClicked,
            paddingValues = paddingValues,
        )
    }
}

@Composable
private fun PosterSectionContent(
    detailedMedia: DetailedMedia,
    isFavourite: Boolean,
    watchButtonText: String,
    trailerUrl: String?,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onToggleFavouritesClicked: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
) {
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val watchButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
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
            model = ImageRequest.Builder(LocalContext.current)
                .data(detailedMedia.baseItem.backgroundUrl)
                .size(600)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .gradientForeground(),
        )

        PosterSectionInfo(
            detailedMedia = detailedMedia,
            isFavourite = isFavourite,
            watchButtonText = watchButtonText,
            trailerUrl = trailerUrl,
            watchButtonFocusRequester = watchButtonFocusRequester,
            onWatchClicked = onWatchClicked,
            onWatchTrailerClicked = onWatchTrailerClicked,
            onToggleFavouritesClicked = onToggleFavouritesClicked,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(top = paddingValues.calculateTopPadding()),
        )
    }
}

@Composable
private fun PosterSectionInfo(
    detailedMedia: DetailedMedia,
    isFavourite: Boolean,
    watchButtonText: String,
    trailerUrl: String?,
    watchButtonFocusRequester: FocusRequester,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onToggleFavouritesClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

        PosterSectionMetInfo(
            imdbRating = detailedMedia.baseItem.imdbRating,
            filmanRating = detailedMedia.baseItem.filmanRating,
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
            isFavourite = isFavourite,
            watchButtonText = watchButtonText,
            trailerUrl = trailerUrl,
            watchButtonFocusRequester = watchButtonFocusRequester,
            onWatchClicked = onWatchClicked,
            onWatchTrailerClicked = onWatchTrailerClicked,
            onToggleFavouritesClicked = onToggleFavouritesClicked,
        )
    }
}

@Composable
private fun PosterSectionMetInfo(
    imdbRating: Rating?,
    filmanRating: Rating?,
    seasonsNumber: Int?,
    duration: Duration?,
    year: Int?,
    countries: List<String>,
    categories: List<String>,
) {
    Row(
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.small)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterSectionMetaInfoRatingItem(
            imdbRating = imdbRating,
            filmanRating = filmanRating,
        )

        seasonsNumber?.let {
            PosterSectionMetaInfoItem(
                icon = null,
                label = pluralStringResource(R.plurals.details_n_seasons, it, it),
                showSeparator = duration != null || year != null || countries.isNotEmpty(),
            )
        }

        duration?.let {
            PosterSectionMetaInfoItem(
                icon = null,
                label = it.toString(),
                showSeparator = year != null || countries.isNotEmpty(),
            )
        }

        year?.let {
            PosterSectionMetaInfoItem(
                icon = null,
                label = it.toString(),
                showSeparator = countries.isNotEmpty(),
            )
        }

        if (countries.isNotEmpty()) {
            PosterSectionMetaInfoItem(
                icon = null,
                label = countries.fastJoinToString(),
                showSeparator = false,
            )
        }
    }

    Row(
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.extraSmall)
            .padding(bottom = MaterialTheme.spacing.small)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.take(3).forEach { category ->
            PosterSectionMetaInfoItem(
                icon = null,
                label = category.titlecase(),
                showDecoration = true,
                showSeparator = false,
            )
        }

        if (categories.size > 3) {
            PosterSectionMetaInfoItem(
                icon = null,
                label = "+${categories.size - 3}",
                showDecoration = true,
                showSeparator = false,
            )
        }
    }
}

@Composable
private fun RowScope.PosterSectionMetaInfoRatingItem(
    imdbRating: Rating?,
    filmanRating: Rating?,
) {
    if (imdbRating != null) {
        Text(
            modifier = Modifier
                .background(
                    color = ImdbColor,
                    shape = RoundedCornerShape(15),
                )
                .padding(
                    vertical = 2.dp,
                    horizontal = 4.dp,
                ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            text = stringResource(R.string.imdb),
            textAlign = TextAlign.Center,
            color = Color.Black,
        )
    } else if (filmanRating != null) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(R.drawable.ic_star_filled),
            contentDescription = null,
            tint = ImdbColor,
        )
    }

    (imdbRating ?: filmanRating)?.let { rating ->
        Text(
            text = "%.1f".format(rating.score),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun RowScope.PosterSectionMetaInfoItem(
    @DrawableRes icon: Int?,
    label: String,
    showDecoration: Boolean = false,
    showSeparator: Boolean = true,
) {
    Row(
        modifier = if (showDecoration) {
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    shape = CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
                .padding(
                    vertical = MaterialTheme.spacing.small,
                    horizontal = MaterialTheme.spacing.medium,
                )
        } else {
            Modifier
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Text(
            text = label,
            style = if (showDecoration) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    if (showSeparator) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PosterSectionCTA(
    isFavourite: Boolean,
    watchButtonText: String,
    trailerUrl: String?,
    watchButtonFocusRequester: FocusRequester,
    onWatchClicked: () -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onToggleFavouritesClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.medium)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmanButton(
            text = watchButtonText,
            iconRes = R.drawable.ic_play,
            onClick = onWatchClicked,
            modifier = Modifier
                .wrapContentWidth()
                .focusRequester(watchButtonFocusRequester)
                .withFocusRestoration("${FEATURED.prefix}watch_button"),
        )

        AnimatedVisibility(
            visible = trailerUrl != null,
            enter = fadeIn() + expandHorizontally(clip = false),
        ) {
            FilmanButton(
                text = stringResource(R.string.details_watch_trailer),
                iconRes = R.drawable.ic_trailer,
                onClick = { onWatchTrailerClicked(trailerUrl.orEmpty()) },
                modifier = Modifier
                    .wrapContentWidth()
                    .withFocusRestoration("${FEATURED.prefix}watch_trailer_button"),
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onBackground,
            )
        }

        FilmanIconButton(
            icon = if (isFavourite) {
                R.drawable.ic_favorite
            } else {
                R.drawable.ic_favorite_empty
            },
            contentDescription = if (isFavourite) {
                R.string.remove_from_favorites
            } else {
                R.string.add_to_favorites
            },
            onClick = onToggleFavouritesClicked,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        )
    }
}
