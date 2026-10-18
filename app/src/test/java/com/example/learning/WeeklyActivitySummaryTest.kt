package com.example.learning

import com.example.data.FocusSession
import com.example.data.RewardLog
import com.example.data.StudyTask
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyActivitySummaryTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun time(day: Int, hour: Int): Long = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.SEPTEMBER, day, hour, 0)
    }.timeInMillis

    @Test fun `counts focus minutes and finished plans separately`() {
        val now = time(30, 12)
        val sessions = listOf(
            FocusSession(durationMinutes = 25, sessionType = "focus", timestamp = time(28, 8)),
            FocusSession(durationMinutes = 15, sessionType = "focus", timestamp = time(28, 9)),
            FocusSession(durationMinutes = 5, sessionType = "break", timestamp = time(28, 10)),
            FocusSession(durationMinutes = 30, sessionType = "focus", timestamp = time(27, 8)),
            FocusSession(durationMinutes = 40, sessionType = "focus", timestamp = time(30, 13))
        )
        val rewards = listOf(
            RewardLog(pointsEarned = 25, reason = "Hoàn thành kế hoạch: SQL", timestamp = time(29, 8)),
            RewardLog(pointsEarned = 150, reason = "Achieved study goal: Kotlin", timestamp = time(30, 8)),
            RewardLog(pointsEarned = 100, reason = "Focus", timestamp = time(30, 9)),
            RewardLog(pointsEarned = 25, reason = "Hoàn thành kế hoạch: Old", timestamp = time(27, 8)),
            RewardLog(pointsEarned = 25, reason = "Hoàn thành kế hoạch: Future", timestamp = time(30, 13))
        )
        val tasks = listOf(
            StudyTask(userId = "alice", title = "SQL", category = "Database", isCompleted = true),
            StudyTask(userId = "alice", title = "Kotlin", category = "Programming", isCompleted = true, completedAt = time(30, 8)),
            StudyTask(userId = "alice", title = "Old", category = "Programming", isCompleted = true),
            StudyTask(userId = "alice", title = "Future", category = "Programming", isCompleted = true)
        )

        val result = WeeklyActivitySummary.calculate(sessions, tasks, rewards, now, utc)

        assertEquals(40f, result.focusMinutesByDay[0])
        assertEquals(0f, result.focusMinutesByDay[6])
        assertEquals(40f, result.focusMinutesByDay.sum())
        assertEquals(2, result.completedPlans)
    }

    @Test fun `new account with no activity has no invented statistics`() {
        val result = WeeklyActivitySummary.calculate(emptyList(), emptyList(), emptyList(), time(28, 8), utc)

        assertEquals(0f, result.focusMinutesByDay.sum())
        assertEquals(0, result.completedPlans)
    }
}
