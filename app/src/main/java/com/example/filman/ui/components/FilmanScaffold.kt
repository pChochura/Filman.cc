package com.example.filman.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

@Composable
internal fun FilmanScaffold(
    navigationTopBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val measurableTopBar = subcompose("TopBar", navigationTopBar).map {
            it.measure(looseConstraints)
        }
        val measurableContent = subcompose(
            slotId = "Content",
            content = {
                content(
                    PaddingValues(
                        top = measurableTopBar.maxOfOrNull { it.height }?.toDp() ?: 0.dp,
                    ),
                )
            },
        ).map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            measurableContent.forEach { it.place(0, 0) }
            measurableTopBar.forEach { it.place(0, 0) }
        }
    }
}
