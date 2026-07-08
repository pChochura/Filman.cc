package com.example.filman.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class FilterOption(val id: String, val label: String)

@Immutable
data class FilterData(
    val sortingOptions: List<FilterOption> = emptyList(),
    val qualityOptions: List<FilterOption> = emptyList(),
    val versionOptions: List<FilterOption> = emptyList(),
    val categoryOptions: List<FilterOption> = emptyList(),
    val yearOptions: List<FilterOption> = emptyList()
)
