package com.example.learning

/** Anchors the server deadline to a monotonic clock, unaffected by device date changes. */
class QuizClock {
    private var deadlineElapsed = 0L
    fun sync(deadline: Long, serverNow: Long, elapsedNow: Long) {
        deadlineElapsed = elapsedNow + (deadline - serverNow).coerceAtLeast(0)
    }
    fun remainingSeconds(elapsedNow: Long): Int =
        ((deadlineElapsed - elapsedNow).coerceAtLeast(0) / 1000).toInt()
}
