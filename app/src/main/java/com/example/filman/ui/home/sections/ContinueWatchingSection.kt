package com.example.filman.ui.home.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.continueWatchingSection(
    items: List<ProgressItem>,
) {
    item(key = "continue_watching_section") {
        ContinueWatchingSectionContent(items)
    }
}

@Composable
private fun ContinueWatchingSectionContent(
    items: List<ProgressItem>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.extraLarge,
                vertical = MaterialTheme.spacing.large,
            ),
            text = stringResource(R.string.home_continue_watching),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
        ) {
            items.forEach { item ->
                ContinueWatchingSectionItem(item)
            }
        }
    }
}

@Composable
private fun ContinueWatchingSectionItem(item: ProgressItem) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceDefaults.scale(),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.medium,
            ),
        ),
    ) {
        AsyncImage(
            modifier = Modifier
                .width(300.dp)
                .aspectRatio(1.5f),
            model = item.posterUrl,
            contentScale = ContentScale.Crop,
            contentDescription = item.titlePl,
        )
    }
}
