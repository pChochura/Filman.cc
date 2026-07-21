package com.example.filman.ui.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.filman.R
import com.example.filman.data.model.ActorDetails
import com.example.filman.data.model.Rating
import com.example.filman.ui.core.horizontalBleed
import com.example.filman.ui.core.selectableBorder
import com.example.filman.ui.theme.ImdbColor
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.launch

internal fun LazyGridScope.actorInfoSection(
    actorDetails: ActorDetails?,
) {
    if (actorDetails == null) return

    item(
        key = "actor_info_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "ActorInfoContent",
    ) {
        ActorInfoContent(
            actorDetails = actorDetails,
        )
    }
}

@Composable
private fun ActorInfoContent(
    actorDetails: ActorDetails,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                if (it.hasFocus) {
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .focusable()
            .focusRestorer(),
    ) {
        Row(
            modifier = modifier.padding(
                vertical = MaterialTheme.spacing.extraLarge,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(IntrinsicSize.Min),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(actorDetails.avatarUrl)
                        .size(200)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(avatarWidth)
                        .aspectRatio(0.75f)
                        .clip(MaterialTheme.shapes.medium)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ),
                )

                actorDetails.filmwebRating?.let { ActorInfoSectionRating(it) }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = actorDetails.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                ActorInfoSectionItemsRow(actorDetails)

                actorDetails.description?.takeIf { it.isNotEmpty() }?.let {
                    var showWholeDescription by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier
                            .horizontalBleed(MaterialTheme.spacing.small)
                            .selectableBorder(
                                shape = MaterialTheme.shapes.small,
                                selectedBorderWidth = 1.dp,
                                selectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                unselectedBorderWidth = 0.dp,
                                unselectedColor = Color.Transparent,
                            )
                            .padding(MaterialTheme.spacing.small),
                        onClick = { showWholeDescription = !showWholeDescription },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            focusedContainerColor = Color.Transparent,
                        ),
                        scale = ClickableSurfaceDefaults.scale(
                            focusedScale = 1f,
                            pressedScale = 0.99f,
                        ),
                        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = if (showWholeDescription) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActorInfoSectionItemsRow(actorDetails: ActorDetails) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actorDetails.birthDate?.let {
            ActorInfoSectionItem(
                icon = R.drawable.ic_calendar,
                label = it,
            )

            if (actorDetails.birthPlace != null || actorDetails.height != null) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        actorDetails.birthPlace?.let {
            ActorInfoSectionItem(
                icon = R.drawable.ic_location,
                label = it,
            )

            if (actorDetails.height != null) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        actorDetails.height?.let {
            ActorInfoSectionItem(
                icon = R.drawable.ic_height,
                label = it,
            )
        }
    }
}

@Composable
private fun ActorInfoSectionItem(
    icon: Int,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActorInfoSectionRating(rating: Rating) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.small,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(
                vertical = MaterialTheme.spacing.medium,
                horizontal = MaterialTheme.spacing.large,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.filmweb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_star),
                contentDescription = null,
                tint = ImdbColor,
            )

            Text(
                text = rating.score.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = ImdbColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val avatarWidth = 150.dp
