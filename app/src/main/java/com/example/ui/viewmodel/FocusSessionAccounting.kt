package com.example.ui.viewmodel

internal object FocusSessionAccounting {
    fun wholeMinutes(seconds: Int): Int = seconds.coerceAtLeast(0) / 60
}
