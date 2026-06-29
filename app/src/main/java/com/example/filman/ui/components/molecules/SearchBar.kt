package com.example.filman.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.theme.spacing

@Composable
fun SearchBar(
    searchQuery: String,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusRequester = remember { FocusRequester() }

        BasicTextField(
            value = searchQuery,
            onValueChange = { onEvent(HomeEvent.OnSearchQueryChanged(it)) },
            textStyle = TextStyle(color = Color.White),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    onEvent(HomeEvent.OnSearchSubmit)
                },
            ),
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(Color.DarkGray)
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.small,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    inner()
                }
            },
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
        Button(
            onClick = {
                keyboardController?.hide()
                onEvent(HomeEvent.OnSearchSubmit)
            },
        ) {
            Text(stringResource(R.string.home_go))
        }
    }
}
