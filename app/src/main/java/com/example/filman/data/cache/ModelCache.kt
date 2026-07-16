package com.example.filman.data.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
@PublishedApi
internal data class DiskCacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val policyType: String,
    val durationMillis: Long = 0,
)

class ModelCache(context: Context) {
    @PublishedApi
    internal val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    @PublishedApi
    internal val cacheDir = File(context.cacheDir, "model_cache").apply { mkdirs() }

    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    @PublishedApi
    internal data class CacheEntry(
        val data: Any?,
        val timestamp: Long,
        val policy: CachePolicy,
    )

    suspend inline fun <reified T> getOrFetch(
        key: String,
        policy: CachePolicy,
        noinline invalidateCondition: ((String) -> Boolean)? = null,
        crossinline fetcher: suspend () -> T,
    ): T {
        if (invalidateCondition != null) {
            val keysToRemove = memoryCache.keys().toList().filter { invalidateCondition(it) }
            keysToRemove.forEach { memoryCache.remove(it) }

            withContext(Dispatchers.IO) {
                cacheDir.listFiles()?.filter { invalidateCondition(it.nameWithoutExtension) }
                    ?.forEach { it.delete() }
            }
        }

        if (policy is CachePolicy.AlwaysInvalid) {
            return fetcher()
        }

        val memEntry = memoryCache[key]
        if (memEntry != null) {
            when (val entryPolicy = memEntry.policy) {
                is CachePolicy.TTL -> {
                    if (System.currentTimeMillis() - memEntry.timestamp <= entryPolicy.durationMillis) {
                        @Suppress("UNCHECKED_CAST")
                        return memEntry.data as T
                    } else {
                        memoryCache.remove(key)
                    }
                }

                is CachePolicy.AlwaysInvalid -> {
                    memoryCache.remove(key)
                }
            }
        }

        val diskFile = File(cacheDir, "$key.json")
        if (diskFile.exists()) {
            try {
                val diskJson = withContext(Dispatchers.IO) { diskFile.readText() }
                val diskEntry = json.decodeFromString<DiskCacheEntry<T>>(diskJson)

                var isValid = false
                if (diskEntry.policyType == "TTL") {
                    if (System.currentTimeMillis() - diskEntry.timestamp <= diskEntry.durationMillis) {
                        isValid = true
                    }
                }

                if (isValid) {
                    memoryCache[key] = CacheEntry(
                        data = diskEntry.data,
                        timestamp = diskEntry.timestamp,
                        policy = policy,
                    )
                    return diskEntry.data
                } else {
                    withContext(Dispatchers.IO) { diskFile.delete() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.IO) { diskFile.delete() }
            }
        }

        val result = fetcher()

        memoryCache[key] = CacheEntry(
            data = result,
            timestamp = System.currentTimeMillis(),
            policy = policy,
        )

        try {
            val policyType = when (policy) {
                is CachePolicy.TTL -> "TTL"
            }
            val duration = policy.durationMillis
            val diskEntry = DiskCacheEntry(
                data = result,
                timestamp = System.currentTimeMillis(),
                policyType = policyType,
                durationMillis = duration,
            )
            val jsonStr = json.encodeToString(diskEntry)
            withContext(Dispatchers.IO) {
                diskFile.writeText(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    fun clear() {
        memoryCache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
