package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.R
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.theme.spacing

internal fun LazyGridScope.errorSection(
    errorMessage: TextValue?,
    paddingValues: PaddingValues,
    onRefresh: () -> Unit,
) {
    if (errorMessage == null) return

    item(
        key = "error_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "ErrorSectionContent",
    ) {
        ErrorSectionContent(
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .height(LocalWindowInfo.current.containerDpSize.height)
                .padding(top = paddingValues.calculateTopPadding()),
        )
    }
}

@Composable
private fun ErrorSectionContent(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryButtonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        retryButtonFocusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = MaterialTheme.spacing.medium,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = stringResource(R.string.couldnt_load_movies),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )

        FilmanButton(
            text = stringResource(R.string.refresh),
            iconRes = null,
            onClick = onRefresh,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
