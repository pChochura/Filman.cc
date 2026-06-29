package com.example.filman.data.model

data class FilterOption(val id: String, val label: String)

data class FilterData(
    val sortingOptions: List<FilterOption> = emptyList(),
    val qualityOptions: List<FilterOption> = emptyList(),
    val versionOptions: List<FilterOption> = emptyList(),
    val categoryOptions: List<FilterOption> = emptyList(),
    val yearOptions: List<FilterOption> = emptyList()
)
