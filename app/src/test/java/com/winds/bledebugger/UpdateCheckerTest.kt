package com.winds.bledebugger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun newerVersionIsDetected() {
        assertTrue(isNewerVersion("v1.4", "1.3"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(isNewerVersion("1.3.1", "1.3"))
    }

    @Test
    fun sameOrOlderVersionIsIgnored() {
        assertFalse(isNewerVersion("v1.3.0", "1.3"))
        assertFalse(isNewerVersion("1.2.9", "1.3"))
        assertFalse(isNewerVersion("latest", "1.3"))
    }
}
