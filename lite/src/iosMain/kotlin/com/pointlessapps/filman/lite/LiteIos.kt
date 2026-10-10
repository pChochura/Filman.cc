package com.pointlessapps.filman.lite

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

actual val defaultBaseUrl: String = "http://localhost:8080"

@OptIn(ExperimentalForeignApi::class)
actual fun downloadsDirectory(): String {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String
    val dir = "$documents/lite_downloads"
    FileSystem.SYSTEM.createDirectories(dir.toPath())
    return dir
}

/** Entry point used by the iOS host app. */
fun MainViewController(): UIViewController = ComposeUIViewController { LiteApp() }
