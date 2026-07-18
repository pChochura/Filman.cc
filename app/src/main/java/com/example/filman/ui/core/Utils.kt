package com.example.filman.ui.core

internal fun String.titlecase() = lowercase().replaceFirstChar { it.uppercase() }
