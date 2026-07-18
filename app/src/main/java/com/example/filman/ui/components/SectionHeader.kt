package com.example.filman.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.ui.theme.spacing

@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.padding(
            horizontal = MaterialTheme.spacing.extraLarge,
            vertical = MaterialTheme.spacing.large,
        ),
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
    )
}
