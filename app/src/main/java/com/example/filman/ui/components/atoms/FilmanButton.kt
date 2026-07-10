package com.example.filman.ui.components.atoms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import com.example.filman.ui.core.suppressInitialKeyUp
import com.example.filman.ui.core.suppressKeyRepeat

enum class ButtonStyle {
    Primary,
    Secondary,
    Outlined
}

@Composable
fun FilmanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    content: @Composable BoxScope.() -> Unit,
) {
    when (style) {
        ButtonStyle.Primary -> {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
                    .then(modifier)
                    .suppressKeyRepeat()
                    .suppressInitialKeyUp()
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    focusedContentColor = Color.White,
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .width(IntrinsicSize.Min)
                            .height(IntrinsicSize.Min),
                        contentAlignment = Alignment.Center,
                        content = content,
                    )
                },
            )
        }

        ButtonStyle.Secondary -> {
            Button(
                onClick = onClick,
                modifier = modifier
                    .suppressKeyRepeat()
                    .suppressInitialKeyUp()
                    .height(56.dp),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = Color.White,
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                content = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                        content = content,
                    )
                },
            )
        }

        ButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier
                    .suppressKeyRepeat()
                    .suppressInitialKeyUp()
                    .height(56.dp),
                colors = OutlinedButtonDefaults.colors(
                    contentColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContentColor = Color.White,
                ),
                border = Border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ).let { border ->
                    OutlinedButtonDefaults.border(
                        border = border,
                        focusedBorder = border,
                        disabledBorder = border,
                        focusedDisabledBorder = border,
                        pressedBorder = border,
                    )
                },
                shape = OutlinedButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                content = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                        content = content,
                    )
                },
            )
        }
    }
}
