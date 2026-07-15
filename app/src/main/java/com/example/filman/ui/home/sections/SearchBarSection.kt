package com.example.filman.ui.home.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.core.selectableBorder
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.searchBarSection(
    paddingValues: PaddingValues,
    onSearchRequested: (String) -> Unit,
) {
    item(key = "search_bar") {
        SearchBarSection(
            paddingValues = paddingValues,
            onSearchRequested = onSearchRequested,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun SearchBarSection(
    paddingValues: PaddingValues,
    onSearchRequested: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val state = rememberTextFieldState()
    var isSelected by remember { mutableStateOf(false) }

    TextField(
        state = state,
        modifier = modifier
            .padding(paddingValues)
            .padding(MaterialTheme.spacing.extraLarge)
            .fillMaxWidth()
            .onFocusChanged { isSelected = it.hasFocus }
            .selectableBorder(isSelectedProvider = { isSelected }),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        placeholder = {
            Text(
                text = stringResource(R.string.home_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
            showKeyboardOnFocus = true,
        ),
        onKeyboardAction = {
            onSearchRequested(state.text.toString())
            keyboardController?.hide()
        },
    )
}
