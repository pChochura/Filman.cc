package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

@Composable
@ReadOnlyComposable
internal fun MutableList<TiwiOverlayMenuItem>.buildLanguageSettings(
    effectiveLanguage: String,
    onLanguageSelected: (String) -> Unit,
) {
    val languageOptions = listOf(
        SettingsConstants.Language.ENGLISH to R.string.overlay_menu_language_en,
        SettingsConstants.Language.POLISH to R.string.overlay_menu_language_pl,
    ).map { (langCode, labelRes) ->
        TiwiOverlayMenuItem.Option(
            id = "language_$langCode",
            label = TextValue.StringResource(labelRes),
            isSelected = effectiveLanguage == langCode,
            onClick = { onLanguageSelected(langCode) },
        )
    }

    val currentLabelRes = when (effectiveLanguage) {
        SettingsConstants.Language.POLISH -> R.string.overlay_menu_language_pl
        else -> R.string.overlay_menu_language_en
    }

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "app_language",
            label = TextValue.StringResource(R.string.overlay_menu_language),
            value = stringResource(currentLabelRes),
            items = languageOptions,
        ),
    )
}
