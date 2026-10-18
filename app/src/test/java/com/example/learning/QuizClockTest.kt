package com.example.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizClockTest {
    @Test fun deadlineUsesServerTimeInsteadOfPhoneCalendar() {
        val clock = QuizClock()
        clock.sync(deadline = 1790000090000L, serverNow = 1790000000000L, elapsedNow = 5000)
        assertEquals(90, clock.remainingSeconds(5000))
        assertEquals(30, clock.remainingSeconds(65000))
        assertEquals(0, clock.remainingSeconds(150000))
    }
    @Test fun reopeningExpiredAttemptDoesNotGrantMoreTime() {
        val clock = QuizClock()
        clock.sync(deadline = 1000, serverNow = 9000, elapsedNow = 100)
        assertEquals(0, clock.remainingSeconds(100))
    }
}
