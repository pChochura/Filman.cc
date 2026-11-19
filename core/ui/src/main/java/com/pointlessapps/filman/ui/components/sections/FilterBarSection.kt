package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.TooltipPosition
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FILTER_BAR
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.suppressInitialKeyUp
import com.pointlessapps.filman.ui.core.suppressKeyRepeat
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing

fun LazyGridScope.filterActionSection(
    hasActiveFilters: Boolean,
    onOpenFilterMenu: () -> Unit,
    onResetFilters: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    item(
        key = "filter_action_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "FilterActionSection",
    ) {
        FilterActionRow(
            hasActiveFilters = hasActiveFilters,
            onOpenFilterMenu = onOpenFilterMenu,
            onResetFilters = onResetFilters,
            modifier = modifier,
        )
    }
}

fun LazyGridScope.filterBarSection(
    hasActiveFilters: Boolean,
    onOpenFilterMenu: () -> Unit,
    onResetFilters: () -> Unit = {},
    modifier: Modifier = Modifier,
) = filterActionSection(
    hasActiveFilters = hasActiveFilters,
    onOpenFilterMenu = onOpenFilterMenu,
    onResetFilters = onResetFilters,
    modifier = modifier,
)

@Composable
internal fun FilterActionRow(
    hasActiveFilters: Boolean,
    onOpenFilterMenu: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    top = MaterialTheme.spacing.small,
                    bottom = MaterialTheme.spacing.small,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasActiveFilters) {
            ClearIconButton(
                onClick = onResetFilters,
                contentDescription = stringResource(R.string.filter_reset_all),
                modifier =
                    Modifier
                        .size(48.dp)
                        .withFocusRestoration("${FILTER_BAR.prefix}reset"),
            )
        }

        FilterIconButton(
            hasActiveFilters = hasActiveFilters,
            onClick = onOpenFilterMenu,
            modifier =
                Modifier
                    .size(48.dp)
                    .withFocusRestoration("${FILTER_BAR.prefix}trigger"),
        )
    }
}

@Composable
fun FilterIconButton(
    hasActiveFilters: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipText: String? = stringResource(R.string.filter_button_label),
    tooltipPosition: TooltipPosition = TooltipPosition.Below,
) {
    SubtleIconButtonWithTooltip(
        tooltipText = tooltipText,
        tooltipPosition = tooltipPosition,
    ) { tooltipModifier ->
        IconButton(
            modifier =
                modifier
                    .then(tooltipModifier)
                    .suppressKeyRepeat()
                    .suppressInitialKeyUp()
                    .selectablePulse(shape = MaterialTheme.shapes.medium),
            onClick = onClick,
            scale = ButtonScale.None,
            colors =
                IconButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor =
                        if (hasActiveFilters) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContentColor =
                        if (hasActiveFilters) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                ),
            shape = ButtonDefaults.shape(MaterialTheme.shapes.medium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_filter),
                contentDescription = tooltipText ?: stringResource(R.string.filter_button_label),
            )
        }
    }
}

@Composable
fun ClearIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tooltipText: String? = contentDescription,
    tooltipPosition: TooltipPosition = TooltipPosition.Below,
) {
    SubtleIconButtonWithTooltip(
        tooltipText = tooltipText,
        tooltipPosition = tooltipPosition,
    ) { tooltipModifier ->
        IconButton(
            modifier =
                modifier
                    .then(tooltipModifier)
                    .suppressKeyRepeat()
                    .suppressInitialKeyUp()
                    .selectablePulse(shape = MaterialTheme.shapes.medium),
            onClick = onClick,
            scale = ButtonScale.None,
            colors =
                IconButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            shape = ButtonDefaults.shape(MaterialTheme.shapes.medium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = contentDescription ?: tooltipText,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtleIconButtonWithTooltip(
    tooltipText: String?,
    tooltipPosition: TooltipPosition,
    content: @Composable (Modifier) -> Unit,
) {
    if (tooltipText == null) {
        content(Modifier)
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val tooltipState = rememberTooltipState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            tooltipState.show()
        } else {
            tooltipState.dismiss()
        }
    }

    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                positioning =
                    when (tooltipPosition) {
                        TooltipPosition.Above -> TooltipAnchorPosition.Above
                        TooltipPosition.Below -> TooltipAnchorPosition.Below
                    },
            ),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.surface,
                )
            }
        },
        state = tooltipState,
    ) {
        content(Modifier.onFocusChanged { isFocused = it.isFocused })
    }
}
