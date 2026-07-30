package com.pointlessapps.filman.ui.core

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

sealed interface TextValue {
    data class DynamicString(val value: String) : TextValue
    data class StringResource(@StringRes val resId: Int) : TextValue

    @Composable
    @ReadOnlyComposable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(resId)
    }
}
