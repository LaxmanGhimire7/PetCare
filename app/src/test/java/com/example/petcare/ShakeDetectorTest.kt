package com.example.petcare

import com.example.petcare.ui.home.ShakeDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects the peak window and debounce used by the destructive shake gesture. */
class ShakeDetectorTest {
    @Test fun threeSeparatePeaksTriggerOnce() {
        val detector = ShakeDetector()
        assertFalse(detector.addSample(0f, 0f, 9.8f, 0))
        assertFalse(detector.addSample(25f, 0f, 9.8f, 100))
        detector.addSample(0f, 0f, 9.8f, 200)
        assertFalse(detector.addSample(25f, 0f, 9.8f, 300))
        detector.addSample(0f, 0f, 9.8f, 400)
        assertTrue(detector.addSample(25f, 0f, 9.8f, 500))
        detector.addSample(0f, 0f, 9.8f, 600)
        assertFalse(detector.addSample(25f, 0f, 9.8f, 700))
    }

    @Test fun slowMovementAndWidelySpacedPeaksDoNotTrigger() {
        val detector = ShakeDetector()
        detector.addSample(0f, 0f, 9.8f, 0)
        repeat(20) { assertFalse(detector.addSample(1f, 0f, 9.8f, it * 100L)) }
        detector.clearWindow()
        assertFalse(detector.addSample(25f, 0f, 9.8f, 3_000))
        detector.addSample(0f, 0f, 9.8f, 3_100)
        assertFalse(detector.addSample(25f, 0f, 9.8f, 4_200))
        detector.addSample(0f, 0f, 9.8f, 4_300)
        assertFalse(detector.addSample(25f, 0f, 9.8f, 5_400))
    }
}
