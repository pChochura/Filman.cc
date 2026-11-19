package com.pointlessapps.filman.ui.components.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.core.InterceptVoiceDictation
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing

fun LazyGridScope.searchBarSection(
    searchFieldState: TextFieldState,
    textFieldFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    activeFilterCount: Int,
    onOpenFilterMenu: () -> Unit,
    onSearchRequested: (String) -> Unit,
    onClearSearch: () -> Unit,
) {
    item(
        key = "search_bar_section_header",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "SearchBarSection",
    ) {
        SearchBarSection(
            searchFieldState = searchFieldState,
            textFieldFocusRequester = textFieldFocusRequester,
            paddingValues = paddingValues,
            activeFilterCount = activeFilterCount,
            onOpenFilterMenu = onOpenFilterMenu,
            onSearchRequested = onSearchRequested,
            onClearSearch = onClearSearch,
        )
    }
}

@Composable
private fun SearchBarSection(
    searchFieldState: TextFieldState,
    textFieldFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    activeFilterCount: Int,
    onOpenFilterMenu: () -> Unit,
    onSearchRequested: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasActiveFilters = activeFilterCount > 0
    val shouldShowClearButton = searchFieldState.text.isNotEmpty() || hasActiveFilters

    InterceptVoiceDictation(
        textFieldState = searchFieldState,
        onSubmit = onSearchRequested,
    ) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding(),
                    ).padding(
                        top = MaterialTheme.spacing.extraLarge,
                        bottom = MaterialTheme.spacing.small,
                    )
                    .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            TextField(
                state = searchFieldState,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocusRequester)
                        .withFocusRestoration("search_bar")
                        .focusProperties { left = textFieldFocusRequester }
                        .selectablePulse(
                            shape = MaterialTheme.shapes.medium,
                            focusedScale = 1f,
                            pressedScale = 1f,
                        ),
                shape = MaterialTheme.shapes.medium,
                colors =
                    TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                    ),
                placeholder = {
                    Text(
                        text = stringResource(R.string.common_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                        showKeyboardOnFocus = true,
                    ),
                onKeyboardAction = {
                    onSearchRequested(searchFieldState.text.toString())
                    keyboardController?.hide()
                },
            )

            AnimatedVisibility(shouldShowClearButton) {
                ClearIconButton(
                    onClick = {
                        searchFieldState.clearText()
                        onClearSearch()
                        textFieldFocusRequester.requestFocus()
                    },
                    contentDescription = stringResource(R.string.common_clear),
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
            }

            FilterIconButton(
                hasActiveFilters = hasActiveFilters,
                onClick = onOpenFilterMenu,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .withFocusRestoration("search_filter_button"),
            )
        }
    }
}
