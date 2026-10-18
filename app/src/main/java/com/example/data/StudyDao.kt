package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    // --- Users ---
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUser(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("UPDATE users SET passwordHash = :newHash WHERE username = :username AND passwordHash = :expectedHash")
    suspend fun updatePasswordHashIfUnchanged(username: String, expectedHash: String, newHash: String): Int

    // --- Tasks ---
    @Query("SELECT * FROM study_tasks WHERE userId = :userId ORDER BY isCompleted ASC, dueDate ASC")
    fun getTasksByUserId(userId: String): Flow<List<StudyTask>>

    @Query("SELECT * FROM study_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): StudyTask?

    @Transaction
    suspend fun completePlan(userId: String, taskId: Int): Boolean {
        val task = getTaskById(taskId) ?: return false
        if (task.userId != userId || task.isCompleted) return false
        insertTask(task.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
        insertReward(RewardLog(userId = userId, pointsEarned = 25, reason = "Hoàn thành kế hoạch: ${task.title}"))
        return true
    }

    @Query("SELECT * FROM study_tasks WHERE isCompleted = 0 AND isSmartReminderEnabled = 1 AND userId = :userId")
    suspend fun getActiveTasksWithReminder(userId: String): List<StudyTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTask): Long

    @Query("UPDATE study_tasks SET elapsedTimeSeconds = MAX(elapsedTimeSeconds, :elapsedSeconds), completedAt = CASE WHEN isCompleted = 0 AND MAX(elapsedTimeSeconds, :elapsedSeconds) >= targetMinutes * 60 THEN :completedAt ELSE completedAt END, isCompleted = CASE WHEN isCompleted = 1 OR MAX(elapsedTimeSeconds, :elapsedSeconds) >= targetMinutes * 60 THEN 1 ELSE 0 END WHERE id = :id AND userId = :userId")
    suspend fun updateFocusProgress(id: Int, userId: String, elapsedSeconds: Int, completedAt: Long): Int

    @Transaction
    suspend fun applyFocusProgress(task: StudyTask): FocusProgressResult? {
        val before = getTaskById(task.id) ?: return null
        if (before.userId != task.userId) return null
        if (updateFocusProgress(task.id, task.userId, task.elapsedTimeSeconds, System.currentTimeMillis()) == 0) return null
        val after = getTaskById(task.id) ?: return null
        return FocusProgressResult(after, !before.isCompleted && after.isCompleted)
    }

    @Delete
    suspend fun deleteTask(task: StudyTask)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    // --- Focus Sessions ---
    @Query("SELECT * FROM focus_sessions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getSessionsByUserId(userId: String): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE userId = :userId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getSessionsSince(userId: String, sinceTimestamp: Long): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    // --- Reward Logs ---
    @Query("SELECT * FROM reward_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getRewardsByUserId(userId: String): Flow<List<RewardLog>>

    @Query("SELECT SUM(pointsEarned) FROM reward_logs WHERE userId = :userId")
    fun getTotalPointsFlow(userId: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(log: RewardLog): Long

    @Query("DELETE FROM reward_logs WHERE userId = :userId")
    suspend fun clearRewardsForUser(userId: String)

    // --- Study Group Chat ---
    @Query("SELECT * FROM group_chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC LIMIT :limit")
    fun getGroupMessages(roomId: String, limit: Int = 120): Flow<List<GroupChatEntity>>

    @Query("SELECT COUNT(*) FROM group_chat_messages WHERE roomId = :roomId")
    suspend fun countGroupMessages(roomId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(message: GroupChatEntity): Long

    @Query(
        """
        DELETE FROM group_chat_messages
        WHERE roomId = :roomId
        AND id NOT IN (
            SELECT id FROM group_chat_messages
            WHERE roomId = :roomId
            ORDER BY timestamp DESC
            LIMIT :keep
        )
        """
    )
    suspend fun trimGroupMessages(roomId: String, keep: Int = 120)

    @Query("DELETE FROM focus_sessions WHERE userId = :userId")
    suspend fun clearSessionsForUser(userId: String)

    @Query("DELETE FROM study_tasks WHERE userId = :userId")
    suspend fun clearTasksForUser(userId: String)
}

data class FocusProgressResult(val task: StudyTask, val newlyCompleted: Boolean)
