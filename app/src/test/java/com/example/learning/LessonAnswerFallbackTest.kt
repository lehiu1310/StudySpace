package com.example.learning

import org.junit.Assert.assertTrue
import org.junit.Test

class LessonAnswerFallbackTest {
    @Test
    fun returnsOnlyRelevantSentencesFromTheLesson() {
        val content = "Room là thư viện lưu trữ dữ liệu quan hệ trên thiết bị. ViewModel giữ trạng thái giao diện. WorkManager chạy tác vụ nền có thể trì hoãn."

        val answer = offlineLessonAnswer("Room lưu dữ liệu như thế nào?", content)

        assertTrue(answer.contains("Room là thư viện lưu trữ dữ liệu quan hệ trên thiết bị."))
        assertTrue(!answer.contains("WorkManager"))
    }

    @Test
    fun saysWhenTheLessonDoesNotContainAnAnswer() {
        val answer = offlineLessonAnswer("Công ty nào tạo ra Kotlin?", "Room lưu dữ liệu quan hệ trên thiết bị.")

        assertTrue(answer.contains("chưa tìm thấy thông tin này"))
    }
}
