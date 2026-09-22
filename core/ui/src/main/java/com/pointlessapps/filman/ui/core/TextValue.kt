package com.pointlessapps.filman.ui.core

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.annotation.PluralsRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

sealed interface TextValue {
    data class DynamicString(
        val value: String,
    ) : TextValue

    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : TextValue {
        constructor(
            @StringRes resId: Int,
            vararg args: Any,
        ) : this(resId, args.toList())
    }


    data class PluralResource(
        @PluralsRes val resId: Int,
        val count: Int,
        val args: List<Any> = emptyList(),
    ) : TextValue {
        constructor(
            @PluralsRes resId: Int,
            count: Int,
            vararg args: Any,
        ) : this(resId, count, args.toList())
    }

    @Composable
    @ReadOnlyComposable
    fun asString(): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args.toTypedArray())
            is PluralResource -> pluralStringResource(resId, count, *args.toTypedArray())
        }

    fun asString(context: Context): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args.toTypedArray())
            is PluralResource -> context.resources.getQuantityString(resId, count, *args.toTypedArray())
        }
}
