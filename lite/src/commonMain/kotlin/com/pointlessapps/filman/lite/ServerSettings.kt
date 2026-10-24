package com.pointlessapps.filman.lite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** Backend address, editable at runtime and persisted in a small file next to the downloads. */
class ServerSettings(
    directory: String,
    private val defaultUrl: String,
    private val fileSystem: FileSystem = defaultFileSystem,
) {
    private val file: Path = directory.toPath() / "server_url.txt"
    private val _url = MutableStateFlow(load())
    val url: StateFlow<String> = _url.asStateFlow()

    fun update(newUrl: String) {
        val normalized = normalize(newUrl).ifBlank { defaultUrl }
        _url.value = normalized
        runCatching {
            file.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.write(file) { writeUtf8(normalized) }
        }
    }

    fun reset() = update(defaultUrl)

    private fun load(): String = runCatching {
        if (fileSystem.exists(file)) fileSystem.read(file) { readUtf8() }.trim() else ""
    }.getOrDefault("").ifBlank { defaultUrl }

    companion object {
        fun normalize(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            return if (trimmed.contains("://")) trimmed else "http://$trimmed"
        }
    }
}
