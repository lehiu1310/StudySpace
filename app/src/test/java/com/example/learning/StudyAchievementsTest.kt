package com.example.learning

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAchievementsTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private fun day(dayOfMonth: Int): Long = Calendar.getInstance(zone).apply {
        set(2026, Calendar.OCTOBER, dayOfMonth, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun combinesLessonsQuizzesAndFocusAcrossConsecutiveDays() {
        val streak = calculateStudyStreak(
            focusSessionTimestamps = listOf(day(13)),
            completedPlanTimestamps = emptyList(),
            quizSubmissionTimestamps = listOf(day(11)),
            lessonCompletionTimestamps = listOf(day(12)),
            now = day(13),
            timeZone = zone
        )

        assertEquals(3, streak)
    }

    @Test
    fun continuesFromYesterdayButResetsAfterAMissingDay() {
        assertEquals(2, calculateStudyStreak(emptyList(), emptyList(), listOf(day(12), day(11)), emptyList(), day(13), zone))
        assertEquals(1, calculateStudyStreak(listOf(day(13), day(11)), emptyList(), emptyList(), emptyList(), day(13), zone))
    }

    @Test
    fun unlocksAllRequestedMilestonesAtTheirThresholds() {
        val badges = calculateStudyBadges(5, listOf(8 to 10, 4 to 5) + List(9) { 7 to 10 }, 7).associateBy { it.id }

        assertTrue(badges.getValue("first_lesson").achieved)
        assertTrue(badges.getValue("five_lessons").achieved)
        assertTrue(badges.getValue("ten_quizzes").achieved)
        assertTrue(badges.getValue("quiz_80").achieved)
        assertTrue(badges.getValue("streak_3").achieved)
        assertTrue(badges.getValue("streak_7").achieved)
        assertFalse(calculateStudyBadges(0, listOf(7 to 10), 2).any { it.achieved })
    }
}
