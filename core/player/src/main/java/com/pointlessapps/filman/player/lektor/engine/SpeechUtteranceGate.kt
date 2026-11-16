package com.pointlessapps.filman.player.lektor.engine

import java.util.concurrent.atomic.AtomicLong

internal class SpeechUtteranceGate {
    private val sequence = AtomicLong()
    private val active = AtomicLong()

    fun tryStart(): Long? {
        val id = sequence.incrementAndGet()
        return id.takeIf { active.compareAndSet(0, id) }
    }

    fun isCurrent(id: Long): Boolean = active.get() == id

    fun finish(id: Long): Boolean = active.compareAndSet(id, 0)

    fun cancel() {
        active.set(0)
    }
}
