package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
internal data class CachedStreamResponse(
    val timestamp: Long,
    val response: LinksResponse,
)

internal class StreamResponseDiskCache(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(server: String, key: String, now: Long, ttl: Long): CachedStreamResponse? {
        val file = fileFor(server, key)
        if (!file.exists()) return null
        return try {
            val entry = json.decodeFromString<CachedStreamResponse>(file.readText())
            if (entry.response.status != "READY" || entry.response.links.isEmpty() ||
                now < entry.timestamp || now - entry.timestamp > ttl
            ) {
                delete(file)
                null
            } else {
                entry
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read stream cache entry", e)
            delete(file)
            null
        }
    }

    fun write(server: String, key: String, entry: CachedStreamResponse) {
        try {
            check(directory.isDirectory || directory.mkdirs()) { "Could not create stream cache directory" }
            val destination = fileFor(server, key)
            val temporary = File.createTempFile("stream-", ".tmp", directory)
            try {
                FileOutputStream(temporary).use {
                    it.write(json.encodeToString(entry).toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                check(temporary.renameTo(destination)) { "Could not replace stream cache entry" }
            } finally {
                if (temporary.exists()) delete(temporary)
            }
            val files = directory.listFiles { file -> file.extension == "json" }
                ?: throw IllegalStateException("Could not list stream cache directory")
            files.sortedByDescending { it.lastModified() }.drop(MAX_ENTRIES).forEach(::delete)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write stream cache entry", e)
        }
    }

    fun remove(server: String, key: String) {
        delete(fileFor(server, key))
    }

    fun clear() {
        if (!directory.exists()) return
        val files = directory.listFiles() ?: run {
            Log.w(TAG, "Could not list stream cache directory")
            return
        }
        files.filter { it.isFile }.forEach(::delete)
    }

    private fun fileFor(server: String, key: String): File {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$server\n$key".toByteArray(Charsets.UTF_8))
        val name = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(directory, "$name.json")
    }

    private fun delete(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "Could not delete stream cache entry")
    }

    private companion object {
        const val TAG = "StreamResponseDiskCache"
        const val MAX_ENTRIES = 100
    }
}
