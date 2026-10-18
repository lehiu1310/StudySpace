package com.example.learning

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LearningSessionTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> state(vm: LearningViewModel, name: String, value: T) {
        val field = LearningViewModel::class.java.getDeclaredField(name + "\$delegate")
        field.isAccessible = true
        (field.get(vm) as MutableState<T>).value = value
    }

    @Test fun `logout clears persisted session even while a request is busy`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = LearningViewModel(app)
        vm.bindOwner("session-test")
        val vault = app.getSharedPreferences("online_session_vault", 0)
        vault.edit().putString("session-test", "test-placeholder").commit()
        state(vm, "busy", true)
        state(vm, "signedIn", true)
        state(vm, "onlineRole", "ADMIN")
        state(vm, "offline", true)

        vm.logout()

        assertFalse(vm.signedIn)
        assertFalse(vm.busy)
        assertFalse(vm.offline)
        assertEquals("STUDENT", vm.onlineRole)
        assertFalse(vault.contains("session-test"))
    }

    @Test fun `switching local account clears previous role and cached status`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = LearningViewModel(app)
        state(vm, "onlineRole", "ADMIN")
        state(vm, "offline", true)
        state(vm, "signedIn", true)

        vm.bindOwner("different-account-test")

        assertEquals("STUDENT", vm.onlineRole)
        assertFalse(vm.offline)
        assertFalse(vm.signedIn)
        assertTrue(vm.courses.isEmpty())
        assertNull(vm.progress)
    }

    @Test fun `legacy local profile without a saved server session is not restored`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = LearningViewModel(app)
        val accountState = app.getSharedPreferences("online_account_state", 0)
        val vault = app.getSharedPreferences("online_session_vault", 0)
        accountState.edit().putString("last_owner", "legacy-without-token").commit()
        vault.edit().remove("legacy-without-token").commit()
        var restored = false

        vm.restoreRememberedAccount { _, _ -> restored = true }

        assertFalse(restored)
        assertFalse(vm.signedIn)
        assertFalse(accountState.contains("last_owner"))
    }
}
