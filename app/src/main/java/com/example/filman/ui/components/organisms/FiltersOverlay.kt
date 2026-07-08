package com.example.filman.ui.components.organisms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.FilterData
import com.example.filman.ui.home.HomeState
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.home.FilterState
import com.example.filman.ui.core.suppressKeyRepeat
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay

@Composable
fun FiltersOverlay(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onClose: () -> Unit,
) {
    val filterState =
        if (state.selectedTabIndex == 1) state.moviesFilterState else state.seriesFilterState
    var currentFilterState by remember { mutableStateOf(filterState) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    val mainFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }

    LaunchedEffect(activeCategory) {
        delay(100)
        if (activeCategory == null) {
            mainFocusRequester.requestFocus()
        } else {
            detailFocusRequester.requestFocus()
        }
    }

    val availableFilters = (
        if (state.selectedTabIndex == 1) {
        state.moviesFilters
    } else {
        state.seriesFilters
    }
    ) ?: FilterData()

    if (activeCategory == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.filters_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
            )

            if (availableFilters.sortingOptions.isNotEmpty()) {
                ListItem(
                    selected = false,
                    onClick = { activeCategory = "sort" },
                    headlineContent = { Text(stringResource(R.string.filters_sort)) },
                    modifier = Modifier.focusRequester(mainFocusRequester),
                )
            }
            if (availableFilters.qualityOptions.isNotEmpty()) {
                ListItem(
                    selected = false,
                    onClick = { activeCategory = "quality" },
                    headlineContent = { Text(stringResource(R.string.filters_quality)) },
                )
            }
            if (availableFilters.versionOptions.isNotEmpty()) {
                ListItem(
                    selected = false,
                    onClick = { activeCategory = "version" },
                    headlineContent = { Text(stringResource(R.string.filters_version)) },
                )
            }
            if (availableFilters.categoryOptions.isNotEmpty()) {
                ListItem(
                    selected = false,
                    onClick = { activeCategory = "category" },
                    headlineContent = { Text(stringResource(R.string.filters_category)) },
                )
            }
            if (availableFilters.yearOptions.isNotEmpty()) {
                ListItem(
                    selected = false,
                    onClick = { activeCategory = "year" },
                    headlineContent = { Text(stringResource(R.string.filters_year)) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Button(
                    onClick = {
                        onEvent(HomeEvent.ClearFilters(state.selectedTabIndex))
                        onClose()
                    },
                    modifier = Modifier.suppressKeyRepeat(),
                ) {
                    Text(stringResource(R.string.filters_clear))
                }
                Button(
                    onClick = {
                        onEvent(HomeEvent.UpdateFilter(state.selectedTabIndex, currentFilterState))
                        onClose()
                    },
                    modifier = Modifier.suppressKeyRepeat(),
                ) {
                    Text(stringResource(R.string.filters_apply))
                }
            }
        }
    } else {
        BackHandler(true) {
            activeCategory = null
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { activeCategory = null },
                    modifier = Modifier
                        .suppressKeyRepeat()
                        .focusRequester(detailFocusRequester),
                ) {
                    Text(stringResource(R.string.filters_back))
                }
                Text(
                    text = activeCategory.toString().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                when (activeCategory) {
                    "sort" -> {
                        items(availableFilters.sortingOptions, key = { it.id }) { option ->
                            ListItem(
                                selected = currentFilterState.sort == option.id,
                                onClick = {
                                    currentFilterState = currentFilterState.copy(sort = option.id)
                                },
                                headlineContent = { Text(option.label) },
                                trailingContent = {
                                    RadioButton(
                                        selected = currentFilterState.sort == option.id,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }

                    "quality" -> {
                        items(availableFilters.qualityOptions, key = { it.id }) { option ->
                            val isSelected = currentFilterState.qualities.contains(option.id)
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    val newQualities = if (isSelected) {
                                        currentFilterState.qualities - option.id
                                    } else {
                                        currentFilterState.qualities + option.id
                                    }
                                    currentFilterState =
                                        currentFilterState.copy(qualities = newQualities)
                                },
                                headlineContent = { Text(option.label) },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                },
                            )
                        }
                    }

                    "version" -> {
                        items(availableFilters.versionOptions, key = { it.id }) { option ->
                            val isSelected = currentFilterState.versions.contains(option.id)
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    val newVersions = if (isSelected) {
                                        currentFilterState.versions - option.id
                                    } else {
                                        currentFilterState.versions + option.id
                                    }
                                    currentFilterState =
                                        currentFilterState.copy(versions = newVersions)
                                },
                                headlineContent = { Text(option.label) },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                },
                            )
                        }
                    }

                    "category" -> {
                        items(availableFilters.categoryOptions, key = { it.id }) { option ->
                            val isSelected = currentFilterState.categories.contains(option.id)
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    val newCategories = if (isSelected) {
                                        currentFilterState.categories - option.id
                                    } else {
                                        currentFilterState.categories + option.id
                                    }
                                    currentFilterState =
                                        currentFilterState.copy(categories = newCategories)
                                },
                                headlineContent = { Text(option.label) },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                },
                            )
                        }
                    }

                    "year" -> {
                        items(availableFilters.yearOptions, key = { it.id }) { option ->
                            val isSelected = currentFilterState.years.contains(option.id)
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    val newYears = if (isSelected) {
                                        currentFilterState.years - option.id
                                    } else {
                                        currentFilterState.years + option.id
                                    }
                                    currentFilterState = currentFilterState.copy(years = newYears)
                                },
                                headlineContent = { Text(option.label) },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
