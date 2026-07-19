package com.example.filman.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.EpisodeLink
import com.example.filman.ui.theme.spacing

@Composable
fun EpisodeCard(
    episode: EpisodeLink,
    posterUrl: String,
    isWatched: Boolean,
    progressPercentage: Float,
    onClick: (EpisodeLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onClick(episode) },
        modifier = modifier
            .width(240.dp)
            .aspectRatio(2f / 1f),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(posterUrl)

                    .size(600)
                    .build(),
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isWatched) 0.5f else 1f,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isWatched) 0.7f else 0.4f)),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (isWatched) {
                    Text(
                        text = stringResource(R.string.details_watched),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = episode.title,
                    color = if (isWatched) Color.LightGray else Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (progressPercentage > 0f && !isWatched) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                Color.DarkGray,
                                RoundedCornerShape(2.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercentage)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}
