package com.pointlessapps.filman.data.api

import okhttp3.Request

class CloudflareChallengeDetected(
    val domain: String = "filman.cc",
    val url: String? = null,
    message: String = "Cloudflare challenge detected",
    val request: Request? = null,
) : Exception(message)
