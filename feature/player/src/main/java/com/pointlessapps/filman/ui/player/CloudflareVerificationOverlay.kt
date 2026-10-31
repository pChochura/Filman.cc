package com.pointlessapps.filman.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.theme.spacing

@Composable
internal fun CloudflareVerificationOverlay(
    modifier: Modifier = Modifier,
    onSwitchSource: () -> Unit,
    onNextSource: () -> Unit,
    onBackClicked: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.medium,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            Text(
                text = stringResource(R.string.player_cloudflare_verification),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.common_verifying_cloudflare_zaluknij),
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilmanButton(
                text = stringResource(R.string.player_switch_source),
                iconRes = R.drawable.ic_swap,
                onClick = onSwitchSource,
            )
            FilmanButton(
                text = stringResource(R.string.player_next_source),
                iconRes = R.drawable.ic_next,
                onClick = onNextSource,
            )
            FilmanButton(
                text = stringResource(R.string.common_back),
                iconRes = R.drawable.ic_back,
                onClick = onBackClicked,
            )
        }
    }
}
