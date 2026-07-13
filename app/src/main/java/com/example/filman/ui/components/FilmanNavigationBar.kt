package com.example.filman.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.example.filman.Route
import com.example.filman.ui.theme.spacing

@Composable
internal fun FilmanNavigationBar(
    currentRouteProvider: () -> Route.Home,
    onRouteChanged: (Route.Home) -> Unit,
    items: List<FilmanNavigationItem>,
) {
    var selectedIndex by remember {
        mutableIntStateOf(items.indexOfFirst { it.route == currentRouteProvider() })
    }
    var itemSizesAndPositions by remember {
        mutableStateOf(items.map { Offset.Zero to 0 }.toTypedArray())
    }

    val currentPosition by animateOffsetAsState(
        itemSizesAndPositions[selectedIndex].first,
    )
    val currentSize by animateIntAsState(
        itemSizesAndPositions[selectedIndex].second,
    )

    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Surface(
        modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
        shape = CircleShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.drawBehind {
                drawRoundRect(
                    color = selectedColor,
                    topLeft = currentPosition,
                    size = Size(currentSize.toFloat(), size.height),
                    cornerRadius = CornerRadius(size.width),
                )
            },
        ) {
            items.forEachIndexed { index, item ->
                NavigationItem(
                    isSelected = selectedIndex == index,
                    item = item,
                    onClick = { onRouteChanged(item.route) },
                    modifier = Modifier.onGloballyPositioned {
                        itemSizesAndPositions[index] = it.positionInParent() to it.size.width
                    },
                )
            }
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
    Button(
        modifier = modifier,
        onClick = onClick,
        shape = ButtonDefaults.shape(
            shape = CircleShape,
        ),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            focusedContainerColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.9f),
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
            text = stringResource(item.title),
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
internal data class FilmanNavigationItem(
    @StringRes val title: Int,
    val route: Route.Home,
)
