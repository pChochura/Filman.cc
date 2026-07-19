package com.example.filman.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.theme.spacing

@Composable
internal fun LoadingMoreFooter() {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(Unit) {
        bringIntoViewRequester.bringIntoView()
    }

    Row(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(
            space = MaterialTheme.spacing.small,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.loading_more),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
