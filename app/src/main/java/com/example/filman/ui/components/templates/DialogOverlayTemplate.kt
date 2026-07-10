package com.example.filman.ui.components.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import com.example.filman.ui.theme.spacing

@Composable
fun DialogOverlayTemplate(
    modifier: Modifier = Modifier,
    width: Dp = 400.dp,
    alignment: Alignment = Alignment.CenterEnd,
    onDismissRequest: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .width(width)
                    .background(Color.Black.copy(alpha = 0.9f))
                    .align(alignment)
                    .padding(MaterialTheme.spacing.extraLarge),
                content = content,
            )
        }
    }
}
