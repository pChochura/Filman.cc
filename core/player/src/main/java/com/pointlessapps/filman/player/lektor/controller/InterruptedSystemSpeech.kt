package com.pointlessapps.filman.player.lektor.controller

internal class InterruptedSystemSpeech {
    private var active: Int? = null
    private var interrupted: Int? = null

    @Synchronized
    fun start(index: Int, speak: () -> Boolean): Boolean {
        if (active != null) return false
        active = index
        if (!speak()) {
            active = null
            return false
        }
        if (interrupted == index) interrupted = null
        return true
    }

    @Synchronized
    fun pause(stop: () -> Unit) {
        if (active != null) interrupted = active
        active = null
        stop()
    }

    @Synchronized
    fun finish() {
        active = null
    }

    @Synchronized
    fun clear() {
        active = null
        interrupted = null
    }

    @Synchronized
    fun interruptedIndex(): Int? = interrupted
}
