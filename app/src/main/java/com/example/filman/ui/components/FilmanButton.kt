package com.example.filman.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
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
    isLoading: Boolean = false,
    fullWidth: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
    focusedContainerColor: Color = MaterialTheme.colorScheme.onBackground,
    contentColor: Color = MaterialTheme.colorScheme.surface,
    focusedContentColor: Color = MaterialTheme.colorScheme.surface,
) {
    Button(
        modifier = modifier
            .selectablePulse(shape = CircleShape)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier),
        onClick = onClick,
        scale = ButtonScale.None,
        colors = ButtonDefaults.colors(
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = ButtonDefaults.shape(CircleShape),
        enabled = !isLoading,
    ) {
        Row(
            modifier = if (fullWidth) Modifier.weight(1f) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.extraSmall,
                alignment = Alignment.CenterHorizontally,
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                iconRes?.let {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(iconRes),
                        contentDescription = null,
                    )
                }

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
