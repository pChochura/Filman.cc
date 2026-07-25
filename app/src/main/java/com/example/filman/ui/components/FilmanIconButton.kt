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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.example.filman.ui.core.selectablePulse

enum class TooltipPosition {
    Top, Bottom
}

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
    tooltipPosition: TooltipPosition = TooltipPosition.Top,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            modifier = modifier
                .selectablePulse()
                .onFocusChanged { isFocused = it.isFocused },
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

        if (contentDescription != null) {
            AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(if (tooltipPosition == TooltipPosition.Top) Alignment.TopCenter else Alignment.BottomCenter)
                    .offset(y = if (tooltipPosition == TooltipPosition.Top) (-40).dp else 40.dp)
                    .zIndex(1f)
            ) {
                Text(
                    text = stringResource(contentDescription),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val DEFAULT_ICON_SIZE = 24.dp
