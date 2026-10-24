package com.pointlessapps.filman.lite.download

import com.pointlessapps.filman.lite.data.StreamSource
import com.pointlessapps.filman.lite.defaultFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

@Serializable
enum class DownloadState { DOWNLOADING, COMPLETED, FAILED }

@Serializable
data class DownloadItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val fileName: String,
    val state: DownloadState = DownloadState.DOWNLOADING,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val error: String? = null,
) {
    val progress: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

class DownloadManager(
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val directory: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val indexPath get() = directory / INDEX_FILE
    private val jobs = mutableMapOf<String, Job>()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    constructor(client: HttpClient, scope: CoroutineScope, directory: String) :
        this(client, scope, directory.toPath())

    fun pathOf(item: DownloadItem): String = (directory / item.fileName).toString()

    fun start(id: String, title: String, posterUrl: String?, source: StreamSource) {
        if (jobs[id]?.isActive == true) return
        val fileName = "${id.filter { it.isLetterOrDigit() || it == '_' }}.mp4"
        val item = DownloadItem(id, title, posterUrl, fileName)
        _items.update { list -> list.filterNot { it.id == id } + item }
        persist()
        jobs[id] = scope.launch { run(item, source) }
    }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        val item = _items.value.firstOrNull { it.id == id } ?: return
        fileSystem.delete(directory / item.fileName)
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private suspend fun run(item: DownloadItem, source: StreamSource) {
        val target = directory / item.fileName
        val partial = directory / "${item.fileName}.part"
        try {
            if (HlsPlaylist.isHls(source.url)) {
                downloadHls(item, source, partial)
                return
            }
            client.prepareGet(source.url) {
                source.headers.forEach { (key, value) -> header(key, value) }
                timeout { requestTimeoutMillis = Long.MAX_VALUE; socketTimeoutMillis = 60_000 }
            }.execute { response ->
                if (!response.status.isSuccess()) error("Server error (${response.status.value})")
                val total = response.contentLength() ?: -1L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_SIZE)
                var written = 0L
                var reported = 0L
                fileSystem.sink(partial).buffer().use { sink ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        if (written - reported >= REPORT_STEP) {
                            reported = written
                            update(item.id) { it.copy(downloadedBytes = written, totalBytes = total) }
                        }
                    }
                }
                fileSystem.atomicMove(partial, target)
                update(item.id) {
                    it.copy(
                        state = DownloadState.COMPLETED,
                        downloadedBytes = written,
                        totalBytes = written,
                        error = null,
                    )
                }
            }
        } catch (e: CancellationException) {
            fileSystem.delete(partial)
            throw e
        } catch (e: Exception) {
            fileSystem.delete(partial)
            update(item.id) { it.copy(state = DownloadState.FAILED, error = e.message ?: "Download failed") }
        } finally {
            jobs.remove(item.id)
        }
    }

    /** Segments are concatenated; fMP4 streams keep .mp4, MPEG-TS streams become .ts. */
    private suspend fun downloadHls(item: DownloadItem, source: StreamSource, partial: Path) {
        suspend fun fetch(url: String): HttpResponse = client.get(url) {
            source.headers.forEach { (key, value) -> header(key, value) }
        }.also { if (!it.status.isSuccess()) error("Server error (${it.status.value})") }

        var playlistUrl = source.url
        var body = fetch(playlistUrl).bodyAsText()
        HlsPlaylist.parseVariants(playlistUrl, body).maxByOrNull { it.bandwidth }?.let {
            playlistUrl = it.url
            body = fetch(playlistUrl).bodyAsText()
        }
        val playlist = HlsPlaylist.parseMedia(playlistUrl, body)
        if (playlist.encrypted) error("Encrypted streams can't be downloaded")
        if (playlist.segments.isEmpty()) error("Empty playlist")

        val parts = listOfNotNull(playlist.initSegment) + playlist.segments
        val finalName = if (playlist.initSegment != null) item.fileName else item.fileName.removeSuffix(".mp4") + ".ts"
        var written = 0L
        fileSystem.sink(partial).buffer().use { sink ->
            parts.forEachIndexed { index, url ->
                currentCoroutineContext().ensureActive()
                val bytes = fetch(url).body<ByteArray>()
                sink.write(bytes)
                written += bytes.size
                // Total size is unknown up front, so progress is reported by segment count.
                update(item.id) {
                    it.copy(downloadedBytes = (index + 1).toLong(), totalBytes = parts.size.toLong())
                }
            }
        }
        fileSystem.atomicMove(partial, directory / finalName)
        update(item.id) {
            it.copy(
                fileName = finalName,
                state = DownloadState.COMPLETED,
                downloadedBytes = written,
                totalBytes = written,
                error = null,
            )
        }
    }

    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persist()
    }

    private fun persist() {
        runCatching {
            fileSystem.createDirectories(directory)
            fileSystem.write(indexPath) {
                writeUtf8(json.encodeToString(ListSerializer(DownloadItem.serializer()), _items.value))
            }
        }
    }

    /** Anything that was in flight when the process died can't resume, so it's marked as failed. */
    private fun load(): List<DownloadItem> = runCatching {
        if (!fileSystem.exists(indexPath)) return emptyList()
        json.decodeFromString(ListSerializer(DownloadItem.serializer()), fileSystem.read(indexPath) { readUtf8() })
            .map {
                if (it.state == DownloadState.DOWNLOADING) {
                    it.copy(state = DownloadState.FAILED, error = "Interrupted")
                } else {
                    it
                }
            }
    }.getOrDefault(emptyList())

    private companion object {
        const val INDEX_FILE = "downloads.json"
        const val BUFFER_SIZE = 64 * 1024
        const val REPORT_STEP = 256 * 1024L
    }
}
