package com.example.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonalStudyStoreTest {
    private lateinit var db: StudyDatabase
    private lateinit var store: PersonalStudyStore
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),StudyDatabase::class.java).allowMainThreadQueries().build()
        store = PersonalStudyStore(db)
    }
    @After fun close() { db.close() }

    @Test fun `restore remaps task references and leaves another account intact`() = runTest {
        val dao=db.studyDao()
        val oldId=dao.insertTask(StudyTask(userId="alice",title="SQL",category="DB",isCompleted=true,completedAt=1234L)).toInt()
        dao.insertSession(FocusSession(userId="alice",taskId=oldId,taskTitle="SQL",durationMinutes=25))
        val bobId=dao.insertTask(StudyTask(userId="bob",title="Kotlin",category="IT")).toInt()
        dao.insertReward(RewardLog(userId="alice",pointsEarned=5,reason="Existing reward"))
        val remote=store.capture("alice")
        store.replace("alice",remote,PersonalStudyStore.hash(remote))
        val task=dao.getTasksByUserId("alice").first().single()
        val session=dao.getSessionsByUserId("alice").first().single()
        assertEquals(task.id,session.taskId)
        assertTrue(task.isCompleted)
        assertEquals(1234L,task.completedAt)
        assertEquals(25,session.durationMinutes)
        assertEquals("bob",dao.getTaskById(bobId)?.userId)
        assertEquals(5,dao.getTotalPointsFlow("alice").first())
    }
    @Test fun `local changes during network request prevent replacement`() = runTest {
        val before=store.capture("alice")
        db.studyDao().insertTask(StudyTask(userId="alice",title="New offline task",category="IT"))
        try {
            store.replace("alice",before,PersonalStudyStore.hash(before))
            fail("Must not erase changes made while awaiting the server")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1,db.studyDao().getTasksByUserId("alice").first().size)
    }
    @Test fun `invalid cloud content rolls back without deleting existing data`() = runTest {
        db.studyDao().insertTask(StudyTask(userId="alice",title="Keep",category="IT"))
        val before=store.capture("alice")
        val invalid=JSONObject(before.toString())
        invalid.getJSONArray("plans").getJSONObject(0).put("targetMinutes",-1)
        try {
            store.replace("alice",invalid,PersonalStudyStore.hash(before))
            fail("Invalid duration must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(PersonalStudyStore.hash(before),PersonalStudyStore.hash(store.capture("alice")))
    }
    @Test fun `hash does not depend on JSON object property order`() {
        assertEquals(PersonalStudyStore.hash(JSONObject("{\"a\":1,\"b\":2}")),PersonalStudyStore.hash(JSONObject("{\"b\":2,\"a\":1}")))
    }
    @Test fun `normalizing an older cloud snapshot supplies an empty completion timestamp`() {
        val old = JSONObject("""{"plans":[{"id":1,"isCompleted":false}],"sessions":[]}""")

        val normalized = PersonalStudyStore.normalize(old)

        assertEquals(0L, normalized.getJSONArray("plans").getJSONObject(0).getLong("completedAt"))
        assertFalse(old.getJSONArray("plans").getJSONObject(0).has("completedAt"))
    }
    @Test fun `applied hash does not absorb changes made after restoring`() = runTest {
        val before=store.capture("alice")
        val result=store.replace("alice",before,PersonalStudyStore.hash(before))
        assertEquals(result.appliedHash,PersonalStudyStore.hash(store.capture("alice")))
        db.studyDao().insertTask(StudyTask(userId="alice",title="Unsynced edit",category="IT"))
        assertNotEquals(result.appliedHash,PersonalStudyStore.hash(store.capture("alice")))
    }
}
