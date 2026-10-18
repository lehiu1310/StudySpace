package com.example.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusCountdownTest {
    @Test fun `remaining time catches up after delayed callback`() {
        assertEquals(1500, FocusCountdown.remainingSeconds(1500, 0))
        assertEquals(1500, FocusCountdown.remainingSeconds(1500, 999))
        assertEquals(1494, FocusCountdown.remainingSeconds(1500, 6500))
        assertEquals(0, FocusCountdown.remainingSeconds(1500, 1_500_000))
        assertEquals(0, FocusCountdown.remainingSeconds(1500, 2_000_000))
    }

    @Test fun `resuming from paused time does not include earlier time`() {
        assertEquals(286, FocusCountdown.remainingSeconds(300, 14_000))
        assertEquals(300, FocusCountdown.remainingSeconds(300, -1000))
    }

    @Test fun `partial focus sessions carry seconds without inflating minutes`() {
        var recordedSeconds = 0
        val elapsedAtPauses = listOf(25, 65, 95, 125)
        val minutes = elapsedAtPauses.map { elapsed ->
            FocusSessionAccounting.wholeMinutes(elapsed - recordedSeconds).also {
                recordedSeconds += it * 60
            }
        }

        assertEquals(listOf(0, 1, 0, 1), minutes)
        assertEquals(120, recordedSeconds)
        assertEquals(0, FocusSessionAccounting.wholeMinutes(59))
    }
}
