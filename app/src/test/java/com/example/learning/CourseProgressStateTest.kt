package com.example.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseProgressStateTest {
    @Test fun zeroProgressIsNotStarted() {
        assertEquals(CourseProgressState.NOT_STARTED, courseProgressState(0, 8))
        assertEquals(CourseProgressState.NOT_STARTED, courseProgressState(0, 0))
        assertEquals(CourseProgressState.NOT_STARTED, courseProgressState(1, 0))
    }

    @Test fun partialProgressIsInProgress() {
        assertEquals(CourseProgressState.IN_PROGRESS, courseProgressState(1, 8))
        assertEquals(CourseProgressState.IN_PROGRESS, courseProgressState(7, 8))
    }

    @Test fun allLessonsCompleteIsComplete() {
        assertEquals(CourseProgressState.COMPLETE, courseProgressState(8, 8))
        assertEquals(CourseProgressState.COMPLETE, courseProgressState(9, 8))
    }
}
