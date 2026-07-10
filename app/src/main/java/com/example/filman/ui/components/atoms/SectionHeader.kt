package com.example.filman.ui.components.atoms

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.ui.theme.spacing

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier
            .padding(top = MaterialTheme.spacing.extraLarge)
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
            .padding(bottom = MaterialTheme.spacing.medium),
    )
}
