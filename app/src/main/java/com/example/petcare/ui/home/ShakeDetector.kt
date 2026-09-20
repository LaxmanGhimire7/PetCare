package com.example.petcare.ui.home

import kotlin.math.sqrt

/** Filters gravity and requires three distinct acceleration peaks in one second. */
class ShakeDetector {
    private val gravity = FloatArray(3)
    private val crossings = ArrayDeque<Long>()
    private var initialized = false
    private var aboveThreshold = false
    private var lastTriggerAt = Long.MIN_VALUE

    fun addSample(x: Float, y: Float, z: Float, atMillis: Long): Boolean {
        if (!initialized) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            initialized = true
            return false
        }
        val raw = floatArrayOf(x, y, z)
        var squared = 0f
        for (axis in 0..2) {
            // 0.8 keeps the slow gravity component while allowing short shake peaks through.
            gravity[axis] = GRAVITY_ALPHA * gravity[axis] + (1f - GRAVITY_ALPHA) * raw[axis]
            val linear = raw[axis] - gravity[axis]
            squared += linear * linear
        }
        val above = sqrt(squared) >= SHAKE_THRESHOLD
        val crossed = above && !aboveThreshold
        aboveThreshold = above
        if (!crossed) return false
        if (lastTriggerAt != Long.MIN_VALUE && atMillis - lastTriggerAt < DEBOUNCE_MS)
            return false
        crossings.addLast(atMillis)
        while (crossings.isNotEmpty() && atMillis - crossings.first() > WINDOW_MS)
            crossings.removeFirst()
        if (crossings.size < REQUIRED_PEAKS) return false
        crossings.clear()
        lastTriggerAt = atMillis
        return true
    }

    fun clearWindow() {
        crossings.clear()
        aboveThreshold = false
    }

    companion object {
        private const val GRAVITY_ALPHA = 0.8f
        // 12 m/s² of linear acceleration is stronger than ordinary phone scrolling.
        private const val SHAKE_THRESHOLD = 12f
        private const val REQUIRED_PEAKS = 3
        private const val WINDOW_MS = 1_000L
        private const val DEBOUNCE_MS = 2_000L
    }
}
