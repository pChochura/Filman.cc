package com.pointlessapps.filman.ui.components.sections

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.ui.core.titlecase
import com.pointlessapps.filman.ui.theme.ImdbColor
import com.pointlessapps.filman.ui.theme.spacing
import kotlin.time.Duration

@Composable
fun PosterSectionMetaInfo(
    imdbRating: Rating?,
    filmanRating: Rating?,
    seasonsNumber: Int?,
    duration: Duration?,
    year: Int?,
    countries: List<String>,
    categories: List<String>,
) {
    Row(
        modifier =
            Modifier
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
                label = pluralStringResource(R.plurals.common_n_seasons, it, it),
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

    if (categories.isNotEmpty()) {
        Row(
            modifier =
                Modifier
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
}

@Composable
private fun RowScope.PosterSectionMetaInfoRatingItem(
    imdbRating: Rating?,
    filmanRating: Rating?,
) {
    if (imdbRating != null) {
        Text(
            modifier =
                Modifier
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
            text = stringResource(R.string.common_imdb),
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
            text = "%.1f".format(rating.normalizedScore),
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
        modifier =
            if (showDecoration) {
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
            style =
                if (showDecoration) {
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
