package com.example.filman.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Suppresses repeated key-down events for center/enter keys on a TV remote,
 * ensuring that click actions fire at most once per physical key press.
 *
 * On a TV remote, holding the D-pad center or Enter sends continuous
 * KeyDown events with increasing repeatCount. Without this guard those
 * repeated events propagate to a focusable Surface or Button and keep
 * re-triggering its onClick handler.
 *
 * Apply this modifier to any Button or Surface that performs a one-shot
 * action (navigation, state change, etc.) before the onClick modifier.
 */
fun Modifier.suppressKeyRepeat(): Modifier = this.onKeyEvent { event ->
    event.type == KeyEventType.KeyDown &&
        event.nativeKeyEvent.repeatCount > 0 &&
        (
            event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter
        )
    // Consume the repeated key-down event so the platform click
    // dispatcher never sees it and onClick is not called again.
}

/**
 * Suppresses a stray KeyUp event that might arrive immediately after this
 * component gains focus. This commonly happens when a component is shown and
 * focused as a result of a LongClick (KeyDown + long wait), and the subsequent
 * KeyUp is delivered to the newly focused component.
 */
@Composable
fun Modifier.suppressInitialKeyUp(): Modifier {
    var hasSeenKeyDown by remember { mutableStateOf(false) }
    return this.onPreviewKeyEvent { event ->
        val isActionKey = event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter

        if (isActionKey) {
            if (event.type == KeyEventType.KeyDown) {
                hasSeenKeyDown = true
                false
            } else if (event.type == KeyEventType.KeyUp) {
                if (!hasSeenKeyDown) {
                    true // Consume the orphaned KeyUp
                } else {
                    hasSeenKeyDown = false // Reset
                    false
                }
            } else {
                false
            }
        } else {
            false
        }
    }
}
