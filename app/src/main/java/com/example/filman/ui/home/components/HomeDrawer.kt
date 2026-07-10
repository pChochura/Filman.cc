package com.example.filman.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.theme.spacing

@Composable
fun NavigationDrawerScope.HomeDrawer(
    isSearchVisible: Boolean,
    selectedTabIndex: Int,
    onEvent: (HomeEvent) -> Unit,
    drawerFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        Color.Transparent,
                    ),
                    startX = 0f,
                    endX = 1000f,
                ),
            )
            .padding(MaterialTheme.spacing.medium)
            .focusGroup()
            .focusRestorer(drawerFocusRequester),
        verticalArrangement = Arrangement.Center,
    ) {
        // Search button — first item, receives focus to open the drawer
        NavigationDrawerItem(
            selected = isSearchVisible,
            onClick = { onEvent(HomeEvent.OnSearchVisibleChanged(!isSearchVisible)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.home_search_drawer),
                )
            },
            modifier = Modifier
                .focusRequester(drawerFocusRequester)
                .focusProperties { right = contentFocusRequester },
        ) {
            Text(stringResource(R.string.home_search_drawer))
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

        // Tabs
        val tabIcons = listOf(
            R.drawable.ic_home to stringResource(R.string.home_tab_home),
            R.drawable.ic_movie to stringResource(R.string.home_movies),
            R.drawable.ic_series to stringResource(R.string.home_series),
            R.drawable.ic_kids to stringResource(R.string.home_kids),
        )

        tabIcons.forEachIndexed { index, (icon, title) ->
            NavigationDrawerItem(
                selected = selectedTabIndex == index,
                onClick = { onEvent(HomeEvent.OnTabSelected(index)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = title,
                    )
                },
                modifier = Modifier.focusProperties { right = contentFocusRequester },
            ) {
                Text(title)
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout button
        NavigationDrawerItem(
            selected = false,
            onClick = { onEvent(HomeEvent.OnLogoutClick) },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                    contentDescription = "Logout",
                )
            },
            modifier = Modifier.focusProperties { right = contentFocusRequester },
        ) {
            Text(stringResource(R.string.home_logout))
        }
    }
}
