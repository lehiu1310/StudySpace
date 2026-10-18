package com.example.learning

import com.example.data.FocusSession
import com.example.data.RewardLog
import com.example.data.StudyTask
import java.util.Calendar
import java.util.TimeZone

data class WeeklyActivity(val focusMinutesByDay: FloatArray, val completedPlans: Int)

object WeeklyActivitySummary {
    fun calculate(
        sessions: List<FocusSession>,
        tasks: List<StudyTask>,
        rewards: List<RewardLog>,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): WeeklyActivity {
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekStart = calendar.timeInMillis
        val minutes = FloatArray(7)
        sessions.filter { it.sessionType == "focus" && it.timestamp in weekStart..now }.forEach { session ->
            calendar.timeInMillis = session.timestamp
            val day = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            minutes[day] += session.durationMinutes
        }
        val completedAtCount = tasks.count { task ->
            task.isCompleted && task.completedAt?.let { it in weekStart..now } == true
        }
        val legacyCompletions = tasks.asSequence()
            .filter { it.isCompleted && it.completedAt == null }
            .groupingBy { it.title }
            .eachCount()
            .toMutableMap()
        var legacyCount = 0
        rewards.filter { it.timestamp in weekStart..now }.forEach { reward ->
            val title = when {
                reward.pointsEarned == 25 && reward.reason.startsWith("Hoàn thành kế hoạch: ") ->
                    reward.reason.removePrefix("Hoàn thành kế hoạch: ")
                reward.pointsEarned == 150 && reward.reason.startsWith("Hoàn thành mục tiêu học tập: ") ->
                    reward.reason.removePrefix("Hoàn thành mục tiêu học tập: ")
                reward.pointsEarned == 150 && reward.reason.startsWith("Achieved study goal: ") ->
                    reward.reason.removePrefix("Achieved study goal: ")
                else -> null
            }
            val remaining = title?.let { legacyCompletions[it] } ?: 0
            if (remaining > 0) {
                legacyCompletions[title!!] = remaining - 1
                legacyCount++
            }
        }
        return WeeklyActivity(minutes, completedAtCount + legacyCount)
    }
}
