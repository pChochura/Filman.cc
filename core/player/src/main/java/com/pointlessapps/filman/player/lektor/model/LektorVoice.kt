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
    val edgeVoiceName: String? = null,
) {
    EDGE(
        id = "edge_marek",
        displayName = "Marek Neural (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "pl-PL-MarekNeural",
    ),
    EDGE_ZOFIA(
        id = "edge_zofia",
        displayName = "Zofia Neural (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "pl-PL-ZofiaNeural",
    ),
    EDGE_ANDREW(
        id = "edge_andrew",
        displayName = "Andrew Multilingual (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "en-US-AndrewMultilingualNeural",
    ),
    EDGE_AVA(
        id = "edge_ava",
        displayName = "Ava Multilingual (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "en-US-AvaMultilingualNeural",
    ),
    EDGE_BRIAN(
        id = "edge_brian",
        displayName = "Brian Multilingual (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "en-US-BrianMultilingualNeural",
    ),
    EDGE_EMMA(
        id = "edge_emma",
        displayName = "Emma Multilingual (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "en-US-EmmaMultilingualNeural",
    ),
    EDGE_REMY(
        id = "edge_remy",
        displayName = "Remy Multilingual (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "fr-FR-RemyMultilingualNeural",
    ),
    EDGE_VIVIENNE(
        id = "edge_vivienne",
        displayName = "Vivienne Multilingual (online)",
        downloadUrl = "",
        archiveDirName = "",
        modelFileName = "",
        edgeVoiceName = "fr-FR-VivienneMultilingualNeural",
    ),
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
        val DEFAULT = EDGE
        private const val SYSTEM_VOICE_PREFIX = "system:"

        fun fromId(id: String?): LektorVoice {
            if (systemVoiceName(id) != null) return SYSTEM
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }

        fun systemVoiceId(name: String): String {
            require(name.isNotBlank())
            return SYSTEM_VOICE_PREFIX + name
        }

        fun systemVoiceName(id: String?): String? =
            id?.takeIf { it.startsWith(SYSTEM_VOICE_PREFIX) }
                ?.removePrefix(SYSTEM_VOICE_PREFIX)
                ?.takeIf { it.isNotBlank() }
    }

    val isOnline: Boolean get() = edgeVoiceName != null
    val requiresModel: Boolean get() = !isSystem && !isOnline
}
