package com.example.filman.ui.components.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Default
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.sectionFocusRestorer
import com.example.filman.ui.core.selectableBorder
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.FEATURED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.featuredSection(
    items: List<MovieItem>,
    paddingValues: PaddingValues,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "featured_section") {
        FeaturedSectionContent(
            items = items,
            paddingValues = paddingValues,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            modifier = Modifier
                .animateItem()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun LazyItemScope.FeaturedSectionContent(
    items: List<MovieItem>,
    paddingValues: PaddingValues,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
    var sectionHasFocus by remember { mutableStateOf(false) }
    val focusRequesters = remember(items) { items.map { FocusRequester() } }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(focusedIndex, items.size) {
        if (items.isNotEmpty()) {
            while (true) {
                delay(5.seconds)
                focusedIndex = (focusedIndex + 1) % items.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillParentMaxWidth()
            .fillParentMaxHeight(0.9f)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { sectionHasFocus = it.hasFocus }
            .focusGroup()
            .sectionFocusRestorer(FEATURED.prefix, focusRequesters.firstOrNull() ?: Default),
    ) {
        FeaturedSectionCarousel(
            items = items,
            paddingValues = paddingValues.plus(
                PaddingValues(bottom = itemRowHeight),
            ),
            focusedIndexProvider = { focusedIndex },
        )

        FeaturedSectionItems(
            items = items,
            focusRequesters = focusRequesters,
            focusedIndexProvider = { focusedIndex },
            sectionHasFocusProvider = { sectionHasFocus },
            onItemFocused = {
                focusedIndex = it
                coroutineScope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            },
            onItemClicked = { onItemClicked(items[it]) },
            onItemLongClicked = { onItemLongClicked(items[it]) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .heightIn(max = itemRowHeight),
        )
    }
}

@Composable
private fun FeaturedSectionCarousel(
    items: List<MovieItem>,
    paddingValues: PaddingValues,
    focusedIndexProvider: () -> Int,
) {
    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = focusedIndexProvider(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "FeaturedCarouselTransition",
    ) { index ->
        val currentItem = items.getOrNull(index) ?: return@AnimatedContent

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = currentItem.backgroundUrl,
                contentDescription = currentItem.titlePl,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .gradientBackground(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding(),
                    )
                    .padding(bottom = MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.spacing.extraLarge)
                        .fillMaxWidth(0.6f),
                    text = currentItem.titlePl,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = MaterialTheme.spacing.extraLarge)
                        .fillMaxWidth(0.6f),
                    text = currentItem.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedSectionItems(
    items: List<MovieItem>,
    focusRequesters: List<FocusRequester>,
    focusedIndexProvider: () -> Int,
    sectionHasFocusProvider: () -> Boolean,
    onItemFocused: (index: Int) -> Unit,
    onItemClicked: (index: Int) -> Unit,
    onItemLongClicked: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val bringIntoViewSpec = LocalBringIntoViewSpec.current
    val itemWidthPx = with(LocalDensity.current) { itemWidth.roundToPx() }
    val spaceWidthPx = with(LocalDensity.current) { MaterialTheme.spacing.large.roundToPx() }
    val startPaddingPx = with(LocalDensity.current) { MaterialTheme.spacing.extraLarge.roundToPx() }
    val listWidth = remember { mutableIntStateOf(0) }

    LaunchedEffect(focusRequesters) {
        snapshotFlow(focusedIndexProvider).collectLatest { focusedIndex ->
            if (sectionHasFocusProvider()) {
                focusRequesters.getOrNull(focusedIndex)?.requestFocus()
            } else if (listWidth.intValue > 0) {
                val itemStartPx = startPaddingPx + focusedIndex * (itemWidthPx + spaceWidthPx)
                val offsetInViewport = itemStartPx - scrollState.value
                val scrollDistance = bringIntoViewSpec.calculateScrollDistance(
                    offset = offsetInViewport.toFloat(),
                    size = itemWidthPx.toFloat(),
                    containerSize = listWidth.intValue.toFloat(),
                )
                scrollState.animateScrollTo(scrollState.value + scrollDistance.toInt())
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.small)
            .onGloballyPositioned { listWidth.intValue = it.size.width }
            .horizontalScroll(scrollState)
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
            .focusProperties {
                onEnter = { focusRequesters.getOrNull(focusedIndexProvider())?.requestFocus() }
            }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            FeaturedSectionItem(
                item = item,
                isSelectedProvider = { focusedIndexProvider() == index },
                onFocused = { onItemFocused(index) },
                onClicked = { onItemClicked(index) },
                onLongClicked = { onItemLongClicked(index) },
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .withFocusRestoration("${FEATURED.prefix}${item.url}")
                    .focusProperties {
                        if (index == 0) {
                            left = focusRequesters.last()
                        }

                        if (index == items.lastIndex) {
                            right = focusRequesters.first()
                        }
                    },
            )
        }
    }
}

@Composable
private fun FeaturedSectionItem(
    item: MovieItem,
    isSelectedProvider: () -> Boolean,
    onFocused: () -> Unit,
    onClicked: () -> Unit,
    onLongClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClicked,
        onLongClick = onLongClicked,
        modifier = modifier
            .onFocusChanged { if (it.hasFocus) onFocused() }
            .width(IntrinsicSize.Min),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shape = MaterialTheme.shapes.medium
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .weight(1f)
                    .clip(shape)
                    .selectableBorder(isSelectedProvider = isSelectedProvider),
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.titlePl,
                    contentScale = ContentScale.Crop,
                )

                item.filmanRating?.let { rating ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(MaterialTheme.spacing.small)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f))
                            .padding(
                                horizontal = MaterialTheme.spacing.extraSmall,
                                vertical = MaterialTheme.spacing.extraSmall / 2,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall / 2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                        )

                        Text(
                            text = rating.score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = item.titlePl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val itemWidth = 100.dp
private val itemRowHeight = 160.dp
