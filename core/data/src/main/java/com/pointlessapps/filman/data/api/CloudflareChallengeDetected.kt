package com.pointlessapps.filman.data.api

class CloudflareChallengeDetected(
    val domain: String = "filman.cc",
    val url: String? = null,
    message: String = "Cloudflare challenge detected",
) : Exception(message)
