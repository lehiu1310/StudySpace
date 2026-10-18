package com.example.ui.viewmodel

internal object FocusCountdown {
    fun remainingSeconds(startSeconds: Int, elapsedMilliseconds: Long): Int {
        val elapsedSeconds = elapsedMilliseconds.coerceAtLeast(0L) / 1000L
        return (startSeconds.toLong() - elapsedSeconds).coerceAtLeast(0L).toInt()
    }
}
