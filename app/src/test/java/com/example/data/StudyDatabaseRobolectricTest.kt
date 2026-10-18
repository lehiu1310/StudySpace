package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudyDatabaseRobolectricTest {
    private lateinit var database: StudyDatabase
    private lateinit var dao: StudyDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, StudyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.studyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persists study user task session and reward`() = runTest {
        val user = User(
            username = "alice",
            displayName = "Alice",
            passwordHash = "hash",
            email = "alice@example.com"
        )

        dao.insertUser(user)
        val taskId = dao.insertTask(
            StudyTask(
                userId = "alice",
                title = "Luyen tap Android",
                category = "Lap trinh",
                targetMinutes = 45
            )
        ).toInt()
        dao.insertSession(
            FocusSession(
                userId = "alice",
                taskId = taskId,
                taskTitle = "Luyen tap Android",
                durationMinutes = 25,
                sessionType = "focus"
            )
        )
        dao.insertReward(
            RewardLog(
                userId = "alice",
                pointsEarned = 10,
                reason = "test"
            )
        )

        assertEquals("Alice", dao.getUser("alice")?.displayName)
        assertNotNull(dao.getTaskById(taskId))
        assertEquals(1, dao.getTasksByUserId("alice").first().size)
        assertEquals(1, dao.getSessionsByUserId("alice").first().size)
        assertEquals(10, dao.getTotalPointsFlow("alice").first() ?: 0)
    }

    @Test
    fun `password hash upgrade only changes matching legacy hash`() = runTest {
        dao.insertUser(User(username = "alice", displayName = "Alice", passwordHash = "legacy"))

        assertEquals(0, dao.updatePasswordHashIfUnchanged("alice", "stale-legacy", "bcrypt"))
        assertEquals("legacy", dao.getUser("alice")?.passwordHash)
        assertEquals(1, dao.updatePasswordHashIfUnchanged("alice", "legacy", "bcrypt"))
        assertEquals("bcrypt", dao.getUser("alice")?.passwordHash)
    }

    @Test
    fun `migrates legacy version three without losing user data`() = migrateLegacyDatabase(3)

    @Test
    fun `migrates version four without losing user data`() = migrateLegacyDatabase(4)

    private fun migrateLegacyDatabase(version: Int) = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_v${version}_test.db"
        context.deleteDatabase(name)
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE users (username TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, passwordHash TEXT NOT NULL, email TEXT NOT NULL, phoneNumber TEXT NOT NULL, school TEXT NOT NULL, major TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE study_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, targetMinutes INTEGER NOT NULL, elapsedTimeSeconds INTEGER NOT NULL, isCompleted INTEGER NOT NULL, dueDate INTEGER NOT NULL, isSmartReminderEnabled INTEGER NOT NULL, reminderHour INTEGER NOT NULL, reminderMinute INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, taskId INTEGER, taskTitle TEXT, durationMinutes INTEGER NOT NULL, sessionType TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE reward_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL, pointsEarned INTEGER NOT NULL, reason TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        if (version >= 4) {
            legacy.execSQL("CREATE TABLE group_chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, roomId TEXT NOT NULL, sender TEXT NOT NULL, message TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            legacy.execSQL("INSERT INTO group_chat_messages VALUES (10,'migration-room','Alice','Keep this message',4)")
        }
        legacy.execSQL("INSERT INTO users VALUES ('alice','Alice','legacy-hash','','','','',1)")
        legacy.execSQL("INSERT INTO study_tasks VALUES (7,'alice','Legacy plan','Study','Keep me',25,600,0,1000,1,20,0,1)")
        legacy.execSQL("INSERT INTO focus_sessions VALUES (8,'alice',7,'Legacy plan',10,'focus',2)")
        legacy.execSQL("INSERT INTO reward_logs VALUES (9,'alice',25,'Legacy reward',3)")
        legacy.version = version
        legacy.close()

        val migrated = Room.databaseBuilder(context, StudyDatabase::class.java, name)
            .addMigrations(*StudyDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = migrated.studyDao()
            assertEquals(5, migrated.openHelper.writableDatabase.version)
            assertEquals("Alice", dao.getUser("alice")?.displayName)
            assertEquals("Legacy plan", dao.getTaskById(7)?.title)
            assertEquals(600, dao.getTaskById(7)?.elapsedTimeSeconds)
            assertNull(dao.getTaskById(7)?.completedAt)
            assertEquals(1, dao.getSessionsByUserId("alice").first().size)
            assertEquals(25, dao.getTotalPointsFlow("alice").first())
            val messagesBeforeInsert = dao.getGroupMessages("migration-room").first()
            assertEquals(if (version >= 4) 1 else 0, messagesBeforeInsert.size)
            if (version >= 4) assertEquals("Keep this message", messagesBeforeInsert.single().message)

            migrated.openHelper.writableDatabase.query("PRAGMA table_info(study_tasks)").use { cursor ->
                val columns = buildSet {
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
                assertEquals(true, "completedAt" in columns)
            }

            dao.insertGroupMessage(GroupChatEntity(roomId = "migration-room", sender = "Bob", message = "New message", timestamp = 5))
            assertEquals(if (version >= 4) 2 else 1, dao.getGroupMessages("migration-room").first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `persists study group chat messages by room`() = runTest {
        dao.insertGroupMessage(
            GroupChatEntity(
                roomId = "android",
                sender = "Alice",
                message = "Minh vao phong hoc Android"
            )
        )
        dao.insertGroupMessage(
            GroupChatEntity(
                roomId = "exam",
                sender = "Bob",
                message = "Tin nhan phong khac"
            )
        )

        val androidMessages = dao.getGroupMessages("android").first()

        assertEquals(1, androidMessages.size)
        assertEquals("Alice", androidMessages.first().sender)
        assertEquals("Minh vao phong hoc Android", androidMessages.first().message)
        assertEquals(1, dao.countGroupMessages("android"))
    }

    @Test
    fun `plan completion is idempotent and never invents focus time`() = runTest {
        val id = dao.insertTask(StudyTask(userId = "alice", title = "SQL", category = "Database", targetMinutes = 45)).toInt()
        assertEquals(true, dao.completePlan("alice", id))
        assertEquals(false, dao.completePlan("alice", id))
        assertEquals(true, dao.getTaskById(id)?.isCompleted)
        assertNotNull(dao.getTaskById(id)?.completedAt)
        assertEquals(0, dao.getTaskById(id)?.elapsedTimeSeconds)
        assertEquals(25, dao.getTotalPointsFlow("alice").first())
        assertEquals(0, dao.getSessionsByUserId("alice").first().size)
    }

    @Test
    fun `another account cannot complete a plan or earn its reward`() = runTest {
        val id = dao.insertTask(StudyTask(userId = "alice", title = "SQL", category = "Database")).toInt()
        assertEquals(false, dao.completePlan("bob", id))
        assertEquals(false, dao.getTaskById(id)?.isCompleted)
        assertEquals(0, dao.getTotalPointsFlow("bob").first() ?: 0)
        assertEquals(0, dao.getTotalPointsFlow("alice").first() ?: 0)
    }

    @Test
    fun `late focus update cannot recreate a deleted plan`() = runTest {
        val task = StudyTask(userId = "alice", title = "SQL", category = "Database", targetMinutes = 25)
        val id = dao.insertTask(task).toInt()
        val runningSnapshot = task.copy(id = id, elapsedTimeSeconds = 1500)

        dao.deleteTaskById(id)

        assertEquals(null, dao.applyFocusProgress(runningSnapshot))
        assertEquals(null, dao.getTaskById(id))
    }

    @Test
    fun `focus progress keeps a completed plan completed and never moves time backwards`() = runTest {
        val task = StudyTask(userId = "alice", title = "SQL", category = "Database", targetMinutes = 25, elapsedTimeSeconds = 900)
        val id = dao.insertTask(task).toInt()
        val snapshot = task.copy(id = id, elapsedTimeSeconds = 600)
        assertEquals(true, dao.completePlan("alice", id))

        val result = dao.applyFocusProgress(snapshot)

        assertEquals(false, result?.newlyCompleted)
        assertEquals(true, result?.task?.isCompleted)
        assertEquals(900, result?.task?.elapsedTimeSeconds)
    }

    @Test
    fun `focus awards completion only on first transition to done`() = runTest {
        val task = StudyTask(userId = "alice", title = "SQL", category = "Database", targetMinutes = 25)
        val id = dao.insertTask(task).toInt()
        val finishedSnapshot = task.copy(id = id, elapsedTimeSeconds = 1500)

        assertEquals(true, dao.applyFocusProgress(finishedSnapshot)?.newlyCompleted)
        val firstCompletedAt = dao.getTaskById(id)?.completedAt
        assertNotNull(firstCompletedAt)
        assertEquals(false, dao.applyFocusProgress(finishedSnapshot)?.newlyCompleted)
        assertEquals(true, dao.getTaskById(id)?.isCompleted)
        assertEquals(firstCompletedAt, dao.getTaskById(id)?.completedAt)
    }
}
