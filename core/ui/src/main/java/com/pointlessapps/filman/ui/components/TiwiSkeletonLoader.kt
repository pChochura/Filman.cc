package com.pointlessapps.filman.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.theme.spacing

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surface,
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 1200f, translateAnim - 1200f),
        end = Offset(translateAnim, translateAnim),
    )
}

@Composable
fun CardSkeleton(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(brush),
    )
}

@Composable
fun HeroSkeleton(
    modifier: Modifier = Modifier,
) {
    val brush = rememberShimmerBrush()
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.85f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(brush)
            .gradientForeground(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = MaterialTheme.spacing.extraLarge,
                    bottom = MaterialTheme.spacing.extraLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            // Title placeholder
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            )
            // Meta tags row placeholder
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    )
                }
            }
            // Synopsis lines
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                Box(
                    modifier = Modifier
                        .width(480.dp)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                )
                Box(
                    modifier = Modifier
                        .width(380.dp)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                )
            }
            // Button placeholders
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                )
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

@Composable
fun CardsRowSkeleton(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    cardWidth: Dp = if (aspectRatio > 1f) 240.dp else 160.dp,
    cardCount: Int = 6,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        // Section title placeholder
        Box(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .width(180.dp)
                .height(24.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            userScrollEnabled = false,
        ) {
            items(cardCount) {
                CardSkeleton(
                    aspectRatio = aspectRatio,
                    width = cardWidth,
                )
            }
        }
    }
}

@Composable
fun CatalogSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HeroSkeleton()
        CardsRowSkeleton(aspectRatio = 1.5f, cardWidth = 260.dp)
        CardsRowSkeleton(aspectRatio = 0.75f, cardWidth = 170.dp)
    }
}

@Composable
fun DetailsSkeleton(
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.88f).dp
    val brush = rememberShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(brush)
                .gradientForeground(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = MaterialTheme.spacing.extraLarge,
                        bottom = MaterialTheme.spacing.extraLarge,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Box(
                    modifier = Modifier
                        .width(420.dp)
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(520.dp)
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        CardsRowSkeleton(aspectRatio = 1.5f, cardWidth = 260.dp)
    }
}
