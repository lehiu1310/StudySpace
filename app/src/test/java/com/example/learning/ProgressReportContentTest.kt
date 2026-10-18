package com.example.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressReportContentTest {
    @Test
    fun emptyProgressFailsWithHelpfulMessage() {
        val error = runCatching {
            ProgressReportContent.buildLines(JSONObject(), "student", "30/09/2026")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Chưa có dữ liệu học tập hoặc kết quả quiz để xuất báo cáo.", error?.message)
    }

    @Test
    fun courseCatalogAndUnsubmittedQuizDoNotCountAsStudyProgress() {
        val report = JSONObject()
            .put("courses", org.json.JSONArray().put(JSONObject()
                .put("title", "Cơ sở dữ liệu")
                .put("completed_count", 0)
                .put("lesson_count", 5)))
            .put("mastery", org.json.JSONArray().put(JSONObject()
                .put("title", "SQL")
                .put("percentage", JSONObject.NULL)))
            .put("history", org.json.JSONArray().put(JSONObject()
                .put("chapter_title", "SQL cơ bản")
                .put("submitted_at", JSONObject.NULL)))

        val error = runCatching {
            ProgressReportContent.buildLines(report, "student", "30/09/2026")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Chưa có dữ liệu học tập hoặc kết quả quiz để xuất báo cáo.", error?.message)
    }

    @Test
    fun populatedProgressIncludesCourseAndQuizResult() {
        val report = JSONObject()
            .put("courses", org.json.JSONArray().put(JSONObject()
                .put("title", "Cơ sở dữ liệu")
                .put("completed_count", 2)
                .put("lesson_count", 5)))
            .put("mastery", org.json.JSONArray().put(JSONObject()
                .put("title", "SQL")
                .put("percentage", 80)))
            .put("history", org.json.JSONArray().put(JSONObject()
                .put("chapter_title", "SQL cơ bản")
                .put("score", 8)
                .put("total", 10)
                .put("submitted_at", 1_790_740_800_000L)))

        val lines = ProgressReportContent.buildLines(report, "student", "30/09/2026")

        assertTrue(lines.contains("Cơ sở dữ liệu: 2/5 bài"))
        assertTrue(lines.contains("SQL: 80%"))
        assertTrue(lines.any { it.startsWith("SQL cơ bản: 8/10 - ") })
    }
}
