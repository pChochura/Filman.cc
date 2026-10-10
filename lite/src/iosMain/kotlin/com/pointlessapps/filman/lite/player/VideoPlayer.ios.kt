package com.pointlessapps.filman.lite.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(source: PlaybackSource, modifier: Modifier) {
    val player = remember(source) {
        val url = if (source.location.startsWith("/")) {
            NSURL.fileURLWithPath(source.location)
        } else {
            NSURL.URLWithString(source.location)!!
        }
        val options = if (source.headers.isEmpty()) null else mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to source.headers)
        AVPlayer(playerItem = AVPlayerItem(AVURLAsset(uRL = url, options = options)))
    }
    DisposableEffect(player) {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
        player.play()
        onDispose { player.pause() }
    }
    val controller = remember(player) { AVPlayerViewController().apply { this.player = player } }
    UIKitView(
        modifier = modifier,
        factory = { controller.view },
    )
}
