package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.R
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.theme.spacing
import com.pointlessapps.filman.ui.watchhistory.WatchHistorySummary

internal fun LazyGridScope.summarySection(
    summary: WatchHistorySummary?,
    firstItemFocusRequester: FocusRequester,
) {
    if (summary == null) return

    item(
        key = "watch_history_summary_header",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "SummarySection",
    ) {
        SummarySectionContent(
            summary = summary,
            focusRequester = firstItemFocusRequester,
        )
    }
}

@Composable
private fun SummarySectionContent(
    summary: WatchHistorySummary,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(bottom = MaterialTheme.spacing.extraLarge)
            .focusRequester(focusRequester)
            .focusable()
            .horizontalBleed(MaterialTheme.spacing.medium)
            .selectablePulse(
                shape = MaterialTheme.shapes.medium,
                pressedScale = 0.99f,
                focusedScale = 1f,
                borderWidth = 1.dp,
                borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            .padding(MaterialTheme.spacing.medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.watch_history_total_time_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.totalWatchTime.asString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.watch_history_movies_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.moviesCount.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.watch_history_episodes_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.episodesCount.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
