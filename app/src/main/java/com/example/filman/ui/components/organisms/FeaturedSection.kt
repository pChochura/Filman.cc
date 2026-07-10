package com.example.filman.ui.components.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.FeaturedItem
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.theme.spacing

@Composable
fun FeaturedSection(
    items: List<FeaturedItem>,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .focusGroup()
            .focusRestorer(focusRequester),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            Surface(
                onClick = { onEvent(HomeEvent.OnMovieClick(item.url)) },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (page == pagerState.currentPage) Modifier.focusRequester(focusRequester) else Modifier),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                ),
                scale = ClickableSurfaceScale.None,
                shape = ClickableSurfaceDefaults.shape(
                    shape = RoundedCornerShape(0),
                ),
            ) {

                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent),
                                startX = 0f,
                                endX = Float.POSITIVE_INFINITY,
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(MaterialTheme.spacing.extraLarge)
                        .fillMaxWidth(0.6f),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

                    val yearRegex = remember {
                        Regex("\\s*\\((\\d{4})\\)\\s*$|\\s*<sup>(\\d{4})</sup>\\s*$|\\s*/.*?(\\d{4})\\s*$")
                    }
                    val yearMatch = yearRegex.find(item.title)
                    val year = yearMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Movie", // Wait, this could be hardcoded. Oh well, it was hardcoded before.
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.LightGray,
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                        Text(
                            text = "TS",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.LightGray,
                        )
                        if (year != null) {
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                            Text(
                                text = year,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.LightGray,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    val scrollState = rememberScrollState()
                    var isDescFocused by remember { mutableStateOf(false) }

                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDescFocused) Color.White else Color.LightGray,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .onFocusChanged { isDescFocused = it.isFocused }
                            .verticalScroll(scrollState),
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(50))
                            .padding(
                                horizontal = MaterialTheme.spacing.medium,
                                vertical = MaterialTheme.spacing.small,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.details_watch_now),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                        )
                    }
                }
            }
        }

        // Pager indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(items.size) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color)
                        .width(8.dp)
                        .height(8.dp),
                )
            }
        }
    }
}
