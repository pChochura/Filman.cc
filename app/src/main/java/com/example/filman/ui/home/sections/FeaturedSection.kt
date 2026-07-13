package com.example.filman.ui.home.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Default
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.data.model.FeaturedItem
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.featuredSection(
    items: List<FeaturedItem>,
    paddingValues: PaddingValues,
    onItemClicked: (FeaturedItem) -> Unit,
) {
    item(key = "featured_section") {
        FeaturedSectionContent(
            items = items,
            paddingValues = paddingValues,
            onItemClicked = onItemClicked,
        )
    }
}

@Composable
private fun LazyItemScope.FeaturedSectionContent(
    items: List<FeaturedItem>,
    paddingValues: PaddingValues,
    onItemClicked: (FeaturedItem) -> Unit,
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sectionHasFocus by remember { mutableStateOf(false) }
    val focusRequesters = remember(items) { items.map { FocusRequester() } }

    LaunchedEffect(focusedIndex, items.size) {
        if (items.isNotEmpty()) {
            while (true) {
                delay(5.seconds)
                focusedIndex = (focusedIndex + 1) % items.size
            }
        }
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    SubcomposeLayout(
        modifier = Modifier
            .fillParentMaxWidth()
            .fillParentMaxHeight(0.9f)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { sectionHasFocus = it.hasFocus }
            .focusGroup()
            .focusRestorer(focusRequesters.firstOrNull() ?: Default),
    ) { constraints ->
        val itemsPlaceables = subcompose("Items") {
            FeaturedSectionItems(
                items = items,
                focusRequesters = focusRequesters,
                focusedIndex = focusedIndex,
                onItemFocused = {
                    focusedIndex = it
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                },
                onItemClicked = { onItemClicked(items[it]) },
                sectionHasFocus = sectionHasFocus,
            )
        }.map { it.measure(constraints.copy(minHeight = 0)) }

        val itemsHeight = itemsPlaceables.maxBy { it.height }.height.toDp()

        val carouselPlaceables = subcompose("Carousel") {
            FeaturedSectionCarousel(
                items = items,
                paddingValues = paddingValues.plus(
                    PaddingValues(bottom = itemsHeight),
                ),
                focusedIndex = focusedIndex,
            )
        }.map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            carouselPlaceables.forEach { it.place(0, 0) }
            itemsPlaceables.forEach {
                it.place(
                    x = 0,
                    y = constraints.maxHeight - it.height,
                )
            }
        }
    }
}

@Composable
private fun FeaturedSectionCarousel(
    items: List<FeaturedItem>,
    paddingValues: PaddingValues,
    focusedIndex: Int,
) {
    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = focusedIndex,
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
    items: List<FeaturedItem>,
    focusRequesters: List<FocusRequester>,
    focusedIndex: Int,
    onItemFocused: (index: Int) -> Unit,
    onItemClicked: (index: Int) -> Unit,
    sectionHasFocus: Boolean,
) {
    val listWidth = remember { mutableFloatStateOf(0f) }
    val lazyListState = rememberScrollState()
    val itemWidth = with(LocalDensity.current) { (itemWidth).toPx() }

    val bringIntoViewSpec = LocalBringIntoViewSpec.current

    LaunchedEffect(focusedIndex) {
        if (sectionHasFocus) {
            focusRequesters.getOrNull(focusedIndex)?.requestFocus()
        } else {
            val offset = bringIntoViewSpec.calculateScrollDistance(
                offset = focusedIndex * itemWidth,
                size = itemWidth,
                containerSize = listWidth.floatValue,
            )
            lazyListState.animateScrollTo(offset.fastRoundToInt())
        }
    }

    Row(
        modifier = Modifier
            .onSizeChanged { listWidth.floatValue = it.width.toFloat() }
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.large)
            .horizontalScroll(lazyListState)
            .padding(horizontal = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            FeaturedSectionItem(
                item = item,
                isSelected = focusedIndex == index,
                onFocused = { onItemFocused(index) },
                onClicked = { onItemClicked(index) },
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
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
    item: FeaturedItem,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClicked,
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
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .aspectRatio(0.75f)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        width = if (isSelected) MaterialTheme.spacing.extraSmall else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.medium,
                    ),
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.titlePl,
                    contentScale = ContentScale.Crop,
                )

                item.rating?.let { rating ->
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
                            painter = painterResource(com.example.filman.R.drawable.ic_star),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                        )

                        Text(
                            text = rating.toString(),
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
