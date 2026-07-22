package com.example.filman.data.cache

import android.content.Context
import android.net.Uri.encode
import com.example.filman.data.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class StaleDataException(val staleData: Any?, cause: Throwable) : Exception(cause)

@Serializable
@PublishedApi
internal data class DiskCacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val policyType: String,
    val durationMillis: Long = 0,
)

internal class ModelCache(
    context: Context,
    private val sourceManager: SourceManager,
) {
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheDir = File(context.cacheDir, "model_cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    @PublishedApi
    internal data class CacheEntry(
        val data: Any?,
        val timestamp: Long,
        val policy: CachePolicy,
    )

    suspend inline fun <reified T> getOrFetch(
        baseKey: String,
        policy: CachePolicy,
        noinline invalidateCondition: ((String) -> Boolean)? = null,
        crossinline fetcher: suspend () -> T,
    ): T {
        val key = "${baseKey}_${sourceManager.activeSource.name}"
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
                        // expired, but keep for stale data fallback
                        memoryCache.remove(key)
                    }
                }

                is CachePolicy.AlwaysInvalid -> {
                    memoryCache.remove(key)
                }

                is CachePolicy.AlwaysValid -> {
                    @Suppress("UNCHECKED_CAST")
                    return memEntry.data as T
                }
            }
        }

        val filename = encode(key, Charsets.UTF_8.name())
        val diskFile = File(cacheDir, "$filename.json")
        if (diskFile.exists()) {
            try {
                val diskJson = withContext(Dispatchers.IO) { diskFile.readText() }
                val diskEntry = json.decodeFromString<DiskCacheEntry<T>>(diskJson)

                var isValid = false
                if (diskEntry.policyType == "TTL") {
                    if (System.currentTimeMillis() - diskEntry.timestamp <= diskEntry.durationMillis) {
                        isValid = true
                    }
                } else if (diskEntry.policyType == "AlwaysValid") {
                    isValid = true
                }

                if (isValid) {
                    memoryCache[key] = CacheEntry(
                        data = diskEntry.data,
                        timestamp = diskEntry.timestamp,
                        policy = policy,
                    )
                    return diskEntry.data
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val result = try {
            fetcher()
        } catch (e: Exception) {
            val staleMemEntry = memEntry?.data
            if (staleMemEntry != null) {
                throw StaleDataException(staleMemEntry, e)
            }
            if (diskFile.exists()) {
                try {
                    val diskJson = withContext(Dispatchers.IO) { diskFile.readText() }
                    val diskEntry = json.decodeFromString<DiskCacheEntry<T>>(diskJson)
                    throw StaleDataException(diskEntry.data, e)
                } catch (readDiskError: Exception) {
                    throw e
                }
            }
            throw e
        }

        // Clean up invalid disk file if fetch succeeded
        if (diskFile.exists()) {
            withContext(Dispatchers.IO) { diskFile.delete() }
        }

        memoryCache[key] = CacheEntry(
            data = result,
            timestamp = System.currentTimeMillis(),
            policy = policy,
        )

        try {
            val policyType = when (policy) {
                is CachePolicy.TTL -> "TTL"
                is CachePolicy.AlwaysInvalid -> "AlwaysInvalid"
                is CachePolicy.AlwaysValid -> "AlwaysValid"
            }
            val duration = if (policy is CachePolicy.TTL) policy.durationMillis else 0L
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
