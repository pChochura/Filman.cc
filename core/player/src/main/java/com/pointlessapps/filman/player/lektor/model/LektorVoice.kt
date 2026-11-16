package com.pointlessapps.filman.player.lektor.model

enum class LektorVoice(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val archiveDirName: String,
    val modelFileName: String,
    val tokensFileName: String = "tokens.txt",
    val dataDirName: String = "espeak-ng-data",
    val speakerId: Int = 0,
    val noiseScale: Float = 0.667f,
    val noiseScaleW: Float = 0.8f,
    val lengthScale: Float = 1.0f,
    val isSystem: Boolean = false,
) {
    SYSTEM(
        id = "system",
        displayName = "Głos systemowy (szybszy)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        isSystem = true,
    ),
    MESKI(
        id = "meski",
        displayName = "Męski - Lektor (WitoldG)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-meski_wg_glos-medium.tar.bz2",
        archiveDirName = "vits-piper-pl_PL-meski_wg_glos-medium",
        modelFileName = "pl_PL-meski_wg_glos-medium.onnx",
        speakerId = 0,
        noiseScale = 0.72f,
        noiseScaleW = 0.8f,
    ),
    BASS_HIGH(
        id = "bass_high",
        displayName = "Męski - Bass HD (Wysoka jakość)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-bass-high.tar.bz2",
        archiveDirName = "vits-piper-pl_PL-bass-high",
        modelFileName = "pl_PL-bass-high.onnx",
        speakerId = 0,
        noiseScale = 0.667f,
        noiseScaleW = 0.8f,
    ),
    JARVIS(
        id = "jarvis",
        displayName = "Męski - Jarvis (Spokojny)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-jarvis_wg_glos-medium.tar.bz2",
        archiveDirName = "vits-piper-pl_PL-jarvis_wg_glos-medium",
        modelFileName = "pl_PL-jarvis_wg_glos-medium.onnx",
        speakerId = 0,
        noiseScale = 0.667f,
        noiseScaleW = 0.8f,
    ),
    GOSIA(
        id = "gosia",
        displayName = "Żeński (Gosia)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-gosia-medium.tar.bz2",
        archiveDirName = "vits-piper-pl_PL-gosia-medium",
        modelFileName = "pl_PL-gosia-medium.onnx",
        speakerId = 0,
        noiseScale = 0.667f,
        noiseScaleW = 0.8f,
    ),
    DARKMAN(
        id = "darkman",
        displayName = "Męski - Klasyczny (Darkman)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pl_PL-darkman-medium.tar.bz2",
        archiveDirName = "vits-piper-pl_PL-darkman-medium",
        modelFileName = "pl_PL-darkman-medium.onnx",
        speakerId = 0,
        noiseScale = 0.667f,
        noiseScaleW = 0.8f,
    );

    companion object {
        val DEFAULT = MESKI

        fun fromId(id: String?): LektorVoice {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
