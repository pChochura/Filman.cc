package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.core.suppressKeyRepeat
import com.pointlessapps.filman.ui.theme.spacing

fun LazyGridScope.emptyFilterResultsSection(
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    item(
        key = "empty_filter_results",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "EmptyFilterResults",
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.filter_empty_results),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilmanButton(
                modifier = Modifier.suppressKeyRepeat(),
                text = stringResource(R.string.filter_reset_all),
                iconRes = R.drawable.ic_delete,
                onClick = onResetFilters,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
