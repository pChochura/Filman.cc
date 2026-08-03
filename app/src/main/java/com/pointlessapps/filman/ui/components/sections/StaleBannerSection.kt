package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.pointlessapps.filman.R
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.theme.spacing

internal fun LazyGridScope.staleBannerSection(isShowingStaleData: Boolean) {
    if (!isShowingStaleData) return

    item(
        key = "stale_banner_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "StaleBanner",
    ) {
        var isDismissed by rememberSaveable { mutableStateOf(false) }
        
        if (!isDismissed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.medium)
                    .selectablePulse(
                        shape = MaterialTheme.shapes.medium,
                        focusedScale = 1.02f,
                        pressedScale = 1f,
                    ),
                onClick = { isDismissed = true },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    focusedContentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.medium),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_movie),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.stale_data_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                    )
                }
            }
        }
    }
}
