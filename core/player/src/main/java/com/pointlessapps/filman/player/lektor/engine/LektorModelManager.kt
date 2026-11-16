package com.pointlessapps.filman.player.lektor.engine

import android.content.Context
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progress: Float) : ModelDownloadState
    data object Extracting : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}

data class LektorModelPaths(
    val modelPath: String,
    val tokensPath: String,
    val dataDirPath: String,
)

class LektorModelManager(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val _downloadedVoices = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVoices: StateFlow<Set<String>> = _downloadedVoices.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refreshDownloadedVoices()
    }

    fun refreshDownloadedVoices() {
        _downloadedVoices.value = getDownloadedVoices()
    }

    fun getVoiceDir(voice: LektorVoice): File {
        val baseDir = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp", "filman_models")
        return File(baseDir, "lektor_models/${voice.id}").apply { mkdirs() }
    }

    fun isModelReady(voice: LektorVoice): Boolean {
        val dir = getVoiceDir(voice)
        val modelFile = File(dir, voice.modelFileName)
        val tokensFile = File(dir, voice.tokensFileName)
        val dataDir = File(dir, voice.dataDirName)

        return modelFile.exists() && modelFile.length() > 1_000_000L &&
                tokensFile.exists() && tokensFile.length() > 0L &&
                dataDir.exists() && dataDir.isDirectory && (dataDir.list()?.isNotEmpty() == true)
    }

    fun getDownloadedVoices(): Set<String> {
        return LektorVoice.entries.filter { isModelReady(it) }.map { it.id }.toSet()
    }

    fun startDownload(voice: LektorVoice) {
        if (isModelReady(voice)) {
            refreshDownloadedVoices()
            _downloadState.value = ModelDownloadState.Ready
            return
        }
        val current = _downloadState.value
        if (current is ModelDownloadState.Downloading || current is ModelDownloadState.Extracting) {
            return
        }
        scope.launch {
            ensureModelDownloaded(voice)
        }
    }

    fun deleteModel(voice: LektorVoice): Boolean {
        val dir = getVoiceDir(voice)
        val deleted = dir.deleteRecursively()
        dir.mkdirs()
        refreshDownloadedVoices()
        _downloadState.value = ModelDownloadState.Idle
        return deleted
    }

    fun getModelPaths(voice: LektorVoice): LektorModelPaths? {
        if (!isModelReady(voice)) return null
        val dir = getVoiceDir(voice)
        return LektorModelPaths(
            modelPath = File(dir, voice.modelFileName).absolutePath,
            tokensPath = File(dir, voice.tokensFileName).absolutePath,
            dataDirPath = File(dir, voice.dataDirName).absolutePath,
        )
    }

    suspend fun ensureModelDownloaded(voice: LektorVoice): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady(voice)) {
            refreshDownloadedVoices()
            _downloadState.value = ModelDownloadState.Ready
            return@withContext true
        }

        val targetDir = getVoiceDir(voice)
        val cacheDir = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val tempArchive = File(cacheDir, "lektor_${voice.id}.tar.bz2")

        try {
            _downloadState.value = ModelDownloadState.Downloading(0.0f)

            val request = Request.Builder()
                .url(voice.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                _downloadState.value = ModelDownloadState.Error("HTTP ${response.code}: Nie udało się pobrać modelu")
                return@withContext false
            }

            val body = response.body

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempArchive).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _downloadState.value = ModelDownloadState.Downloading(progress)
                        }
                    }
                }
            }

            _downloadState.value = ModelDownloadState.Extracting
            extractTarBz2(tempArchive, targetDir, voice.archiveDirName)

            if (tempArchive.exists()) {
                tempArchive.delete()
            }

            if (isModelReady(voice)) {
                refreshDownloadedVoices()
                _downloadState.value = ModelDownloadState.Ready
                true
            } else {
                refreshDownloadedVoices()
                _downloadState.value = ModelDownloadState.Error("Wypakowany model jest niekompletny")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempArchive.exists()) {
                tempArchive.delete()
            }
            refreshDownloadedVoices()
            _downloadState.value = ModelDownloadState.Error("Błąd pobierania: ${e.localizedMessage}")
            false
        }
    }

    internal fun extractTarBz2(archiveFile: File, outputDir: File, stripPrefix: String) {
        FileInputStream(archiveFile).use { fileIn ->
            BufferedInputStream(fileIn).use { buffIn ->
                BZip2CompressorInputStream(buffIn).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            var entryName = entry.name
                            if (entryName.startsWith(stripPrefix)) {
                                entryName = entryName.removePrefix(stripPrefix).removePrefix("/")
                            }

                            if (entryName.isNotBlank()) {
                                val outFile = File(outputDir, entryName)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { out ->
                                        tarIn.copyTo(out)
                                    }
                                }
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }
    }
}
