package com.example.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardChartsTest {
    @Test fun aiProviderBadgeUsesTheSelectedAppLanguage() {
        assertEquals("Gợi ý offline", aiProviderLabelText("offline", "vi"))
        assertEquals("AI trực tiếp · Gemini", aiProviderLabelText("gemini", "vi"))
        assertEquals("Offline suggestion", aiProviderLabelText("offline", "en"))
        assertEquals("Direct AI · Gemini", aiProviderLabelText("gemini", "en"))
    }

    @Test fun weeklyTrendUsesSubmittedQuizScoresAndGroupsByWeek() {
        val currentWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val previousWeek = currentWeek - 7L * 24 * 60 * 60 * 1000 + 60_000
        val series = weeklyScoreSeries(listOf(
            JSONObject().put("submitted_at", previousWeek).put("score", 2).put("total", 4),
            JSONObject().put("submitted_at", previousWeek + 1_000).put("score", 3).put("total", 4),
            JSONObject().put("submitted_at", System.currentTimeMillis()).put("score", 4).put("total", 4),
            JSONObject().put("submitted_at", System.currentTimeMillis()).put("score", 0).put("total", 0)
        ))

        assertEquals(62, series[4])
        assertEquals(100, series[5])
        assertEquals(null, series[3])
    }
}
