package com.example.filman.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.ui.core.selectablePulse

@Composable
fun FilmanButton(
    text: String,
    @DrawableRes iconRes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.selectablePulse(),
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.9f),
        colors = ButtonDefaults.colors(
            focusedContainerColor = Color.Transparent,
            focusedContentColor = MaterialTheme.colorScheme.surface,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
        shape = ButtonDefaults.shape(CircleShape),
    ) {
        iconRes?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}
