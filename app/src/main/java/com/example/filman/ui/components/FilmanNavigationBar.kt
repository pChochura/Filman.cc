package com.example.filman.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.filman.Route
import com.example.filman.ui.theme.spacing

@Composable
internal fun FilmanNavigationBar(
    currentRouteProvider: () -> Route,
    onRouteChanged: (Route) -> Unit,
    onScrollToTopRequested: () -> Unit,
    items: List<FilmanNavigationItem>,
    contentFocusRequester: FocusRequester,
) {
    var selectedIndex by remember(items) {
        mutableIntStateOf(
            items.indexOfFirst {
                it.route == currentRouteProvider()
            }.coerceAtLeast(0),
        )
    }
    val selectedItemFocusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    BackHandler(!hasFocus) {
        selectedItemFocusRequester.requestFocus()
        onScrollToTopRequested()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .padding(
                top = MaterialTheme.spacing.extraLarge,
                start = MaterialTheme.spacing.extraLarge,
            )
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = if (hasFocus) 0.9f else 0.6f,
                ),
            )
            .padding(MaterialTheme.spacing.extraSmall)
            .focusProperties {
                onEnter = { selectedItemFocusRequester.requestFocus() }
                down = contentFocusRequester
            }
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        items.forEachIndexed { index, item ->
            NavigationItem(
                isSelected = selectedIndex == index,
                item = item,
                onClick = { contentFocusRequester.requestFocus() },
                modifier = Modifier
                    .onFocusChanged {
                        if (it.isFocused) {
                            selectedIndex = index
                            onRouteChanged(item.route)
                        }
                    }
                    .then(
                        when (index) {
                            selectedIndex -> Modifier.focusRequester(selectedItemFocusRequester)
                            items.lastIndex -> Modifier.focusProperties {
                                right = contentFocusRequester
                            }

                            else -> Modifier
                        },
                    )
                    .focusProperties {
                        down = contentFocusRequester
                    },
            )
        }
    }
}

@Composable
private fun NavigationItem(
    isSelected: Boolean,
    item: FilmanNavigationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                } else {
                    Color.Transparent
                },
                shape = CircleShape,
            ),
        shape = ClickableSurfaceDefaults.shape(
            shape = CircleShape,
        ),
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.9f),
    ) {
        when (item) {
            is FilmanNavigationItem.Icon -> Icon(
                modifier = Modifier
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    )
                    .size(16.dp)
                    .align(Alignment.Center),
                painter = painterResource(item.icon),
                contentDescription = stringResource(item.contentDescription),
            )

            is FilmanNavigationItem.Text -> Text(
                modifier = Modifier
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    )
                    .align(Alignment.Center),
                text = stringResource(item.title),
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal sealed interface FilmanNavigationItem {
    val route: Route

    data class Text(
        @StringRes val title: Int,
        override val route: Route,
    ) : FilmanNavigationItem

    data class Icon(
        @DrawableRes val icon: Int,
        @StringRes val contentDescription: Int,
        override val route: Route,
    ) : FilmanNavigationItem
}
