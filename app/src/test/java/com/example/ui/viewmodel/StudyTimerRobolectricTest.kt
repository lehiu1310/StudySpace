package com.example.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudyTask
import com.example.data.StudyDatabase
import com.example.data.User
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudyTimerRobolectricTest {
    @Test fun `pause catches up when timer callback has not run`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudyViewModel(app)
        vm.setSessionMode("break")
        vm.startTimer()

        ShadowSystemClock.advanceBy(Duration.ofSeconds(7))
        vm.pauseTimer()

        assertFalse(vm.timerRunning.value)
        assertEquals(5 * 60 - 7, vm.timerSeconds.value)
    }

    @Test fun `choosing a study goal returns from break to focus`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudyViewModel(app)
        vm.setSessionMode("break")

        vm.selectTaskToFocus(StudyTask(id = 42, userId = "alice", title = "SQL", category = "Database", targetMinutes = 35))

        assertEquals("focus", vm.sessionType.value)
        assertEquals(35 * 60, vm.timerSeconds.value)
        assertEquals(35 * 60, vm.timerMaxSeconds.value)
    }

    @Test fun `legacy local profile is not restored as a separate signed-in account`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val database = StudyDatabase.getDatabase(app)
        val username = "legacy-${System.nanoTime()}"
        val prefs = app.getSharedPreferences("edupomodoro_prefs", 0)
        database.studyDao().insertUser(User(username, "Legacy profile", "legacy-hash"))
        prefs.edit().putString("last_username", username).commit()

        try {
            val vm = StudyViewModel(app)
            assertNull(vm.currentUser.value)
        } finally {
            prefs.edit().remove("last_username").commit()
            database.openHelper.writableDatabase.execSQL("DELETE FROM users WHERE username = ?", arrayOf(username))
        }
    }
}
