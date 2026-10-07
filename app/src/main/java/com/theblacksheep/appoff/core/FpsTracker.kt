package com.theblacksheep.appoff.core

import kotlin.math.roundToInt

/**
 * Calculates an observed frame rate over a monotonic time window.
 * The class is independent of Android so the timing behavior can be unit tested.
 */
internal class FpsTracker(
    private val windowNanos: Long = 1_000_000_000L,
) {
    private var windowStartNanos = -1L
    private var frameCount = 0

    @Synchronized
    fun reset() {
        windowStartNanos = -1L
        frameCount = 0
    }

    /** Returns a new FPS sample when the window is complete, otherwise null. */
    @Synchronized
    fun onFrame(frameTimeNanos: Long): Int? {
        require(frameTimeNanos >= 0) { "frameTimeNanos must not be negative" }

        if (windowStartNanos < 0L) {
            windowStartNanos = frameTimeNanos
        }
        frameCount++

        val elapsedNanos = frameTimeNanos - windowStartNanos
        if (elapsedNanos < windowNanos) return null

        val fps = (frameCount * 1_000_000_000.0 / elapsedNanos)
            .roundToInt()
            .coerceIn(0, 240)
        windowStartNanos = frameTimeNanos
        frameCount = 0
        return fps
    }
}
