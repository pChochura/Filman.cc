package com.example.filman.data.cache

import java.util.concurrent.ConcurrentHashMap

class ModelCache {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val data: Any?,
        val timestamp: Long,
        val policy: CachePolicy
    )

    suspend fun <T> getOrFetch(
        key: String,
        policy: CachePolicy,
        invalidateCondition: ((String) -> Boolean)? = null,
        fetcher: suspend () -> T
    ): T {
        if (invalidateCondition != null) {
            val keysToRemove = cache.keys().toList().filter { invalidateCondition(it) }
            keysToRemove.forEach { cache.remove(it) }
        }

        if (policy is CachePolicy.AlwaysInvalid) {
            return fetcher()
        }

        val entry = cache[key]
        if (entry != null) {
            when (val entryPolicy = entry.policy) {
                is CachePolicy.TTL -> {
                    if (System.currentTimeMillis() - entry.timestamp <= entryPolicy.durationMillis) {
                        @Suppress("UNCHECKED_CAST")
                        return entry.data as T
                    } else {
                        cache.remove(key)
                    }
                }
                is CachePolicy.AlwaysInvalid -> {
                    cache.remove(key)
                }
            }
        }

        val result = fetcher()
        cache[key] = CacheEntry(
            data = result,
            timestamp = System.currentTimeMillis(),
            policy = policy
        )
        return result
    }

    fun clear() {
        cache.clear()
    }
}
