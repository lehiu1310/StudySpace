package com.example.learning

enum class CourseProgressState {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE
}

internal fun courseProgressState(completed: Int, total: Int): CourseProgressState = when {
    total <= 0 -> CourseProgressState.NOT_STARTED
    total > 0 && completed >= total -> CourseProgressState.COMPLETE
    completed > 0 -> CourseProgressState.IN_PROGRESS
    else -> CourseProgressState.NOT_STARTED
}
