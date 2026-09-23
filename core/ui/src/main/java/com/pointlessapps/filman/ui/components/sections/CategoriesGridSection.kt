package com.pointlessapps.filman.ui.components.sections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.serialization.Serializable

@Composable
internal fun CategoriesGridSectionSkeletonRow(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_transition")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "skeleton_translate",
    )

    val spacingExtraLarge = MaterialTheme.spacing.extraLarge
    val spacingLarge = MaterialTheme.spacing.large
    val density = LocalDensity.current
    val itemSpacingPx =
        remember(density, spacingLarge) {
            with(density) { spacingLarge.toPx() }
        }
    val rowSpacingPx =
        remember(density, spacingExtraLarge) {
            with(density) { spacingExtraLarge.toPx() }
        }

    Row(
        modifier =
            modifier
                .then(
                    if (index == CATEGORIES_SKELETON_ROWS_COUNT - 1) {
                        Modifier.padding(bottom = MaterialTheme.spacing.extraLarge)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        repeat(CATEGORIES_ITEM_COUNT_PER_ROW) { i ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(1.5f)
                        .clip(MaterialTheme.shapes.medium)
                        .alpha((CATEGORIES_SKELETON_ROWS_COUNT - index) / CATEGORIES_SKELETON_ROWS_COUNT.toFloat() * 0.5f)
                        .drawWithCache {
                            val itemWidth = size.width
                            val itemHeight = size.height

                            val absoluteX = i * (itemWidth + itemSpacingPx)
                            val absoluteY = index * (itemHeight + rowSpacingPx)

                            val totalWidth =
                                (itemWidth * CATEGORIES_ITEM_COUNT_PER_ROW) + (itemSpacingPx * (CATEGORIES_ITEM_COUNT_PER_ROW - 1))
                            val totalHeight =
                                (itemHeight * CATEGORIES_SKELETON_ROWS_COUNT) + (rowSpacingPx * (CATEGORIES_SKELETON_ROWS_COUNT - 1))

                            val gradientWidth = totalWidth * 0.2f
                            val gradientHeight = totalHeight * 0.2f

                            val startX = -gradientWidth
                            val endX = totalWidth + gradientWidth
                            val startY = -gradientHeight
                            val endY = totalHeight + gradientHeight

                            onDrawBehind {
                                val currentX = startX + (endX - startX) * translateAnim
                                val currentY = startY + (endY - startY) * translateAnim

                                drawRect(
                                    brush =
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    Color.DarkGray,
                                                    Color.LightGray,
                                                    Color.DarkGray,
                                                ),
                                            start =
                                                Offset(
                                                    currentX - gradientWidth - absoluteX,
                                                    currentY - gradientHeight - absoluteY,
                                                ),
                                            end =
                                                Offset(
                                                    currentX + gradientWidth - absoluteX,
                                                    currentY + gradientHeight - absoluteY,
                                                ),
                                        ),
                                )
                            }
                        },
            )
        }
    }
}

@Composable
internal fun CategoriesGridSectionRow(
    isLast: Boolean,
    rowIndex: Int,
    rowItems: List<FilterOption>,
    onItemClicked: (FilterOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .then(
                    if (isLast) {
                        Modifier.padding(bottom = MaterialTheme.spacing.extraLarge)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        rowItems.forEachIndexed { index, item ->
            val ownFocusRequester =
                if (index == 0) {
                    remember { FocusRequester() }
                } else {
                    FocusRequester.Default
                }
            CategoriesGridSectionItem(
                item = item,
                index = rowIndex * CATEGORIES_ITEM_COUNT_PER_ROW + index,
                onItemClicked = { onItemClicked(item) },
                modifier =
                    Modifier
                        .then(
                            if (index == 0) {
                                Modifier
                                    .focusRequester(ownFocusRequester)
                                    .focusProperties { left = ownFocusRequester }
                            } else {
                                Modifier
                            },
                        ),
            )
        }

        repeat(CATEGORIES_ITEM_COUNT_PER_ROW - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun RowScope.CategoriesGridSectionItem(
    item: FilterOption,
    index: Int,
    onItemClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .weight(1f)
                .selectablePulse(
                    shape = MaterialTheme.shapes.medium,
                    focusedScale = 1.1f,
                    pressedScale = 1f,
                ),
        onClick = onItemClicked,
        shape =
            ClickableSurfaceDefaults.shape(
                shape = MaterialTheme.shapes.medium,
            ),
        scale = ClickableSurfaceScale.None,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .drawWithCache {
                        val hue1 = (index * 13) % 360f
                        val hue2 = (hue1 + 60f) % 360f
                        val gradientColors =
                            listOf(
                                Color.hsl(hue1, 0.5f, 0.4f),
                                Color.hsl(hue2, 0.5f, 0.3f),
                            )

                        onDrawBehind {
                            drawRect(
                                brush =
                                    Brush.linearGradient(
                                        colors = gradientColors,
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height),
                                    ),
                            )
                        }
                    },
        )

        Text(
            modifier =
                Modifier
                    .padding(MaterialTheme.spacing.medium)
                    .align(Alignment.Center),
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
@Serializable
internal data class CategoriesChunk(
    val categories: List<FilterOption>,
)

internal const val CATEGORIES_ITEM_COUNT_PER_ROW = 5
internal const val CATEGORIES_SKELETON_ROWS_COUNT = 3
