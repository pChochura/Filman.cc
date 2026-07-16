package com.example.filman.data.cache

sealed class CachePolicy {
    data class TTL(val durationMillis: Long) : CachePolicy()
    object AlwaysInvalid : CachePolicy()
    object AlwaysValid : CachePolicy()
}
