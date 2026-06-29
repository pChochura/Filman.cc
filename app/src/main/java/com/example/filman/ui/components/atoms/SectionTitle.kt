package com.example.filman.ui.components.atoms

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.ui.theme.spacing

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        modifier = modifier.padding(vertical = MaterialTheme.spacing.medium),
        fontWeight = FontWeight.Bold,
    )
}
