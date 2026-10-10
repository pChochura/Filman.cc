package com.pointlessapps.filman.lite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** Cookies captured from a Filman login; the user agent must match the one that produced them. */
data class FilmanCredentials(val cookie: String, val userAgent: String)

/** Persists the Filman login next to the downloads so it survives restarts. */
class FilmanSession(
    directory: String,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private val file: Path = directory.toPath() / "filman_session.txt"
    private val _credentials = MutableStateFlow(load())
    val credentials: StateFlow<FilmanCredentials?> = _credentials.asStateFlow()

    fun save(cookie: String, userAgent: String) {
        if (cookie.isBlank()) return
        val value = FilmanCredentials(cookie.trim(), userAgent.trim())
        _credentials.value = value
        runCatching {
            file.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.write(file) { writeUtf8(value.userAgent + "\n" + value.cookie) }
        }
    }

    fun clear() {
        _credentials.value = null
        runCatching { fileSystem.delete(file) }
    }

    private fun load(): FilmanCredentials? = runCatching {
        if (!fileSystem.exists(file)) return null
        val lines = fileSystem.read(file) { readUtf8() }.lines()
        val cookie = lines.drop(1).joinToString("").trim()
        if (cookie.isEmpty()) null else FilmanCredentials(cookie, lines.first().trim())
    }.getOrNull()
}
