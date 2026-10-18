package com.example.learning

import java.util.Calendar
import java.util.TimeZone

data class StudyBadge(val id: String, val achieved: Boolean)

internal fun calculateStudyStreak(
    focusSessionTimestamps: Iterable<Long>,
    completedPlanTimestamps: Iterable<Long>,
    quizSubmissionTimestamps: Iterable<Long>,
    lessonCompletionTimestamps: Iterable<Long>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): Int {
    fun dayStart(timestamp: Long): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val activeDays = (focusSessionTimestamps.asSequence() + completedPlanTimestamps.asSequence() +
        quizSubmissionTimestamps.asSequence() + lessonCompletionTimestamps.asSequence())
        .map(::dayStart).toSet()
    var cursor = Calendar.getInstance(timeZone).apply {
        timeInMillis = dayStart(now)
        if (timeInMillis !in activeDays) add(Calendar.DAY_OF_YEAR, -1)
    }
    var streak = 0
    while (cursor.timeInMillis in activeDays) {
        streak++
        cursor.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

internal fun calculateStudyBadges(
    completedLessons: Int,
    submittedQuizScores: List<Pair<Int, Int>>,
    streakDays: Int
): List<StudyBadge> {
    val quizCount = submittedQuizScores.size
    val hasHighScore = submittedQuizScores.any { (score, total) -> total > 0 && score.toDouble() / total >= 0.8 }
    return listOf(
        StudyBadge("first_lesson", completedLessons >= 1),
        StudyBadge("five_lessons", completedLessons >= 5),
        StudyBadge("ten_quizzes", quizCount >= 10),
        StudyBadge("quiz_80", hasHighScore),
        StudyBadge("streak_3", streakDays >= 3),
        StudyBadge("streak_7", streakDays >= 7)
    )
}
