package com.pointlessapps.filman

internal suspend fun <T> verifyCurrentProviderSession(
    current: () -> T,
    verify: suspend (T) -> Boolean,
): Boolean {
    val request = current()
    return verify(request) && request == current()
}

internal suspend fun probeExistingProviderSession(
    verify: suspend () -> Boolean,
    onVerified: suspend () -> Unit,
    onWebViewRequired: () -> Unit,
): Boolean {
    if (verify()) {
        onVerified()
        return true
    }
    onWebViewRequired()
    return false
}
