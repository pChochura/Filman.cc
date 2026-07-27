package com.example.filman.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.example.filman.ui.theme.spacing

@Composable
fun FilmanButton(
    text: String,
    @DrawableRes iconRes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.onBackground,
    focusedContainerColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
    contentColor: Color = MaterialTheme.colorScheme.surface,
    focusedContentColor: Color = MaterialTheme.colorScheme.surface,
) {
    Button(
        modifier = Modifier
            .selectablePulse()
            .wrapContentWidth()
            .then(modifier),
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.9f),
        colors = ButtonDefaults.colors(
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = ButtonDefaults.shape(CircleShape),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.extraSmall,
                alignment = Alignment.CenterHorizontally,
            ),
        ) {
            iconRes?.let {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(iconRes),
                    contentDescription = null,
                )
            }

            Text(
                modifier = Modifier.wrapContentWidth(),
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
