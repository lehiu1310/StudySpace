package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyReminderPolicyTest {
    @Test
    fun reminderIsScheduledFifteenMinutesBeforeDueTime() {
        val now = 1_000_000L
        val dueAt = now + 2 * 60 * 60 * 1000L

        assertEquals(105 * 60 * 1000L, StudyReminderPolicy.delayMillis(dueAt, now))
    }

    @Test
    fun reminderIsImmediateWhenDueWithinFifteenMinutes() {
        val now = 1_000_000L

        assertEquals(0L, StudyReminderPolicy.delayMillis(now + 5 * 60 * 1000L, now))
    }

    @Test
    fun overdueTaskHasNoReminder() {
        val now = 1_000_000L

        assertNull(StudyReminderPolicy.delayMillis(now, now))
        assertNull(StudyReminderPolicy.delayMillis(now - 1, now))
    }
}
