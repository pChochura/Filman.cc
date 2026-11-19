package com.pointlessapps.filman.ui.components.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.ui.theme.spacing

fun LazyGridScope.emptySection(
    @StringRes messageRes: Int,
    modifier: Modifier = Modifier,
) {
    item(
        key = "empty_section_$messageRes",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "EmptySection",
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.huge),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
