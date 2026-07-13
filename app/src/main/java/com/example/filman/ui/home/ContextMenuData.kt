package com.example.filman.ui.home

import androidx.compose.runtime.Immutable

@Immutable
data class ContextMenuData(
    val url: String,
    val titlePl: String,
    val posterUrl: String,
    val isProgress: Boolean,
    val seriesUrl: String? = null,
)
