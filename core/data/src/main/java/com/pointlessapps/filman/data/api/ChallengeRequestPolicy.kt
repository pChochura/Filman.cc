package com.pointlessapps.filman.data.api

import android.util.Log
import okhttp3.Request

fun challengeVerificationRequest(request: Request?, domain: String, safePostPath: String? = null): Request? {
    if (request == null) return null
    val host = request.url.host
    val allowedHost = host == domain || host.endsWith(".$domain")
    val allowedMethod = request.method == "GET" ||
        (request.method == "POST" && safePostPath != null && request.url.encodedPath == safePostPath)
    if (request.url.scheme != "https" || !allowedHost || !allowedMethod) {
        Log.w("ChallengeRequestPolicy", "Rejected unsupported provider verification request")
        return null
    }
    return request.newBuilder().removeHeader("Cookie").build()
}
