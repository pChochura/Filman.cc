package com.pointlessapps.filman.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pointlessapps.filman.lite.player.BackHandler

const val FILMAN_LOGIN_URL = "https://filman.cc/logowanie"

/**
 * Embedded browser that opens the Filman login page and reports the session cookies and the
 * user agent once the site shows a logged-in page.
 */
@Composable
expect fun FilmanWebLogin(
    onLoggedIn: (cookie: String, userAgent: String) -> Unit,
    modifier: Modifier = Modifier,
)

/** Forgets the browser's Filman cookies so the next login starts from a clean state. */
expect fun clearFilmanWebSession()

@Composable
internal fun FilmanLoginScreen(
    onLoggedIn: (cookie: String, userAgent: String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true, onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Log in to Filman", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("Close") }
            }
            FilmanWebLogin(onLoggedIn = onLoggedIn, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
