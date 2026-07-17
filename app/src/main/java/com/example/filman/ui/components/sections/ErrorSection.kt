package com.example.filman.ui.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.core.selectableBorder
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.errorSection(
    errorMessage: String?,
    paddingValues: PaddingValues,
    onRefresh: () -> Unit,
) {
    if (errorMessage == null) return

    item(key = "error_section") {
        ErrorSectionContent(
            onRefresh = onRefresh,
            modifier = Modifier
                .fillParentMaxSize()
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
        var isSelected by remember { mutableStateOf(false) }

        Text(
            text = stringResource(R.string.couldnt_load_movies),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )

        Button(
            modifier = Modifier
                .focusRequester(retryButtonFocusRequester)
                .onFocusChanged { isSelected = it.isFocused }
                .selectableBorder(isSelectedProvider = { isSelected }),
            onClick = onRefresh,
            scale = ButtonDefaults.scale(focusedScale = 1f),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = ButtonDefaults.shape(
                shape = MaterialTheme.shapes.medium,
            ),
        ) {
            Text(
                text = stringResource(R.string.refresh),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
