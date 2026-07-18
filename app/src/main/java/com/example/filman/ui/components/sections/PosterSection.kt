package com.example.filman.ui.components.sections

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.DetailedMedia
import com.example.filman.ui.components.FilmanIconButton
import com.example.filman.ui.components.FilmanProgressBar
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.titlecase
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.posterSection(
    detailedMedia: DetailedMedia?,
) {
    if (detailedMedia == null) return

    item(key = "poster_section") {
        PosterSectionContent(
            detailedMedia = detailedMedia,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun LazyItemScope.PosterSectionContent(
    detailedMedia: DetailedMedia,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillParentMaxWidth()
            .fillParentMaxHeight(0.9f),
    ) {
        AsyncImage(
            model = detailedMedia.baseItem.backgroundUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .gradientBackground(),
        )

        PosterSectionInfo(
            detailedMedia = detailedMedia,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun PosterSectionInfo(
    detailedMedia: DetailedMedia,
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
            seasonsNumber = detailedMedia.seasonsNumber,
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

        PosterSectionCTA()
    }
}

@Composable
private fun PosterSectionMetInfo(
    seasonsNumber: Int?,
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
        seasonsNumber?.let {
            PosterSectionMetaInfoItem(
                icon = null,
                label = pluralStringResource(R.plurals.details_n_seasons, it, it),
            )
        }

        year?.let {
            PosterSectionMetaInfoItem(
                icon = null,
                label = it.toString(),
                showSeparator = countries.isNotEmpty(),
            )
        }

        countries.forEachIndexed { index, country ->
            PosterSectionMetaInfoItem(
                icon = null,
                label = country,
                showDecoration = false,
                showSeparator = index < countries.lastIndex,
            )
        }
    }

    Row(
        modifier = Modifier
            .padding(bottom = MaterialTheme.spacing.small)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEach { category ->
            PosterSectionMetaInfoItem(
                icon = null,
                label = category.titlecase(),
                showDecoration = true,
                showSeparator = false,
            )
        }
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
                    shape = MaterialTheme.shapes.small,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(
                    vertical = MaterialTheme.spacing.extraSmall,
                    horizontal = MaterialTheme.spacing.small,
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
            style = MaterialTheme.typography.bodySmall,
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
private fun PosterSectionCTA() {
    Row(
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.medium)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmanIconButton(
            icon = R.drawable.ic_play,
            contentDescription = null,
            onClick = {},
            modifier = Modifier.size(80.dp),
            iconSize = 48.dp,
        )

        Column(
            modifier = Modifier.width(IntrinsicSize.Min),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            // TODO: check if is in progress
            val inProgress = true

            Text(
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.extraSmall,
                ),
                text = stringResource(
                    if (inProgress) {
                        R.string.details_continue
                    } else {
                        R.string.details_watch_now
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (inProgress) {
                FilmanProgressBar(
                    progress = 0.5f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FilmanIconButton(
            icon = R.drawable.ic_favorite,
            contentDescription = null,
            onClick = {},
            modifier = Modifier.size(32.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        )
    }
}
