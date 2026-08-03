package com.pointlessapps.filman.ui.player
import androidx.media3.ui.SubtitleView
import androidx.media3.common.text.CueGroup
import androidx.media3.common.text.Cue
fun test(sv: SubtitleView, cg: CueGroup) {
    sv.setCues(cg.cues)
    // or
    // sv.setCues(cg)
}
