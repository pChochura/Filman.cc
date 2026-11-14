package com.pointlessapps.filman.ui.core

import java.util.Locale

internal val playerAdHosts = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "amazon-adsystem.com",
    "adsystem.com",
)

fun isPlayerAdHost(host: String?): Boolean {
    val normalized = host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
    return playerAdHosts.any { normalized == it || normalized.endsWith(".$it") }
}
