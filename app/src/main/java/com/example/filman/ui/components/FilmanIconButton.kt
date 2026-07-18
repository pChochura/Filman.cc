package com.example.filman.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import com.example.filman.ui.core.selectablePulse

@Composable
internal fun FilmanIconButton(
    @DrawableRes icon: Int,
    @StringRes contentDescription: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = DEFAULT_ICON_SIZE,
    containerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor: Color = MaterialTheme.colorScheme.onSurface,
    focusedContentColor: Color = MaterialTheme.colorScheme.surface,
) {
    IconButton(
        modifier = modifier.selectablePulse(),
        onClick = onClick,
        scale = IconButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.9f),
        colors = IconButtonDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
        ),
        shape = IconButtonDefaults.shape(
            shape = CircleShape,
        ),
    ) {
        Icon(
            modifier = Modifier.size(iconSize),
            painter = painterResource(icon),
            contentDescription = contentDescription?.let { stringResource(it) },
        )
    }
}

private val DEFAULT_ICON_SIZE = 24.dp
