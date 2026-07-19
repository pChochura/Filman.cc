package com.example.filman.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.theme.spacing

@Composable
fun ProgressCard(
    item: ProgressItem.InProgress,
    onClick: (ProgressItem.InProgress) -> Unit,
    onLongClick: ((ProgressItem.InProgress) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val seasonEpisodeRegex1 =
        remember { Regex("(?i)(?:sezon|season)\\s*(\\d+)[\\s-]*(?:odcinek|episode)\\s*(\\d+)") }
    val seasonEpisodeRegex2 = remember { Regex("(?i)s(\\d+)e(\\d+)") }

    var badgeText: String? = null
    var displayTitle = item.titlePl

    val match1 = seasonEpisodeRegex1.find(item.titlePl)
    if (match1 != null) {
        badgeText = "S${match1.groupValues[1]} E${match1.groupValues[2]}"
        val baseTitle = item.titlePl.substring(0, match1.range.first).trim(' ', '-')
        displayTitle = if (!item.parentUrl.isNullOrBlank()) item.parentUrl else baseTitle
    } else {
        val match2 = seasonEpisodeRegex2.find(item.titlePl)
        if (match2 != null) {
            badgeText = "S${match2.groupValues[1]} E${match2.groupValues[2]}"
            val baseTitle = item.titlePl.substring(0, match2.range.first).trim(' ', '-')
            displayTitle = if (!item.parentUrl.isNullOrBlank()) item.parentUrl else baseTitle
        } else if (!item.parentUrl.isNullOrBlank()) {
            val matchUrl = seasonEpisodeRegex2.find(item.url)
            if (matchUrl != null) {
                badgeText = "S${matchUrl.groupValues[1]} E${matchUrl.groupValues[2]}"
            }
            displayTitle = item.parentUrl
        }
    }

    if (displayTitle.isBlank()) {
        displayTitle = item.titlePl
    }

    Card(
        onClick = { onClick(item) },
        onLongClick = onLongClick?.let { { it(item) } },
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.posterUrl)

                        .size(600)
                        .build(),
                    contentDescription = item.titlePl,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                )
            }

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(MaterialTheme.spacing.small)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }

            // Progress Bar and Title overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    modifier = Modifier.padding(MaterialTheme.spacing.small),
                )

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.DarkGray),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progressPercentage)
                            .background(Color.Red),
                    )
                }
            }
        }
    }
}
