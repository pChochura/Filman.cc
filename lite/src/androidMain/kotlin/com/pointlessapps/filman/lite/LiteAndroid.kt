package com.pointlessapps.filman.lite

import android.content.Context
import okio.Path.Companion.toPath
import okio.FileSystem

/** The host app must call [init] before the first composition of `LiteApp`. */
object LiteAndroid {
    internal lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

// 10.0.2.2 is the host machine as seen from the Android emulator.
actual val defaultBaseUrl: String = "http://10.0.2.2:8080"

actual fun downloadsDirectory(): String {
    val dir = LiteAndroid.appContext.filesDir.resolve("lite_downloads")
    FileSystem.SYSTEM.createDirectories(dir.absolutePath.toPath())
    return dir.absolutePath
}
