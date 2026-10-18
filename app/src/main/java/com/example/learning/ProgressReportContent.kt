package com.example.learning

import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

internal object ProgressReportContent {
    fun buildLines(report: JSONObject, username: String, exportedAt: String): List<String> {
        val courses = report.optJSONArray("courses") ?: JSONArray()
        val mastery = report.optJSONArray("mastery") ?: JSONArray()
        val history = report.optJSONArray("history") ?: JSONArray()
        val submittedQuizzes = (0 until history.length())
            .mapNotNull(history::optJSONObject)
            .filterNot { it.isNull("submitted_at") }

        val hasCompletedLessons = (0 until courses.length()).any { index ->
            courses.optJSONObject(index)?.let { it.optInt("completed_count", 0) > 0 } == true
        }
        val hasMasteryResults = (0 until mastery.length()).any { index ->
            mastery.optJSONObject(index)?.let { !it.isNull("percentage") } == true
        }
        require(hasCompletedLessons || hasMasteryResults || submittedQuizzes.isNotEmpty()) {
            "Chưa có dữ liệu học tập hoặc kết quả quiz để xuất báo cáo."
        }

        return buildList {
            add("StudySpace - Kết quả học tập")
            add(username)
            add(exportedAt)
            add("")
            add("Tiến độ môn học")
            if (courses.length() == 0) add("Chưa có dữ liệu môn học.")
            for (index in 0 until courses.length()) {
                val item = courses.getJSONObject(index)
                add("${item.getString("title")}: ${item.getInt("completed_count")}/${item.getInt("lesson_count")} bài")
            }

            add("")
            add("Kết quả theo chương")
            if (mastery.length() == 0) add("Chưa có dữ liệu đánh giá theo chương.")
            for (index in 0 until mastery.length()) {
                val item = mastery.getJSONObject(index)
                val score = if (item.isNull("percentage")) "Chưa có dữ liệu" else "${item.getDouble("percentage").toInt()}%"
                add("${item.getString("title")}: $score")
            }

            add("")
            add("Lịch sử quiz")
            if (submittedQuizzes.isEmpty()) add("Chưa có bài quiz đã nộp.")
            submittedQuizzes.forEach { item ->
                val submittedAt = DateFormat.getDateInstance().format(Date(item.getLong("submitted_at")))
                add("${item.getString("chapter_title")}: ${item.getInt("score")}/${item.getInt("total")} - $submittedAt")
            }
        }
    }
}
