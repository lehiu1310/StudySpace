package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val displayName: String,
    val passwordHash: String,
    val email: String = "",
    val phoneNumber: String = "",
    val school: String = "",
    val major: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_tasks")
data class StudyTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "guest",
    val title: String,
    val category: String, // e.g. "Toán", "Lập trình", "Ngoại ngữ", "Khác"
    val description: String = "",
    val targetMinutes: Int = 25,
    val elapsedTimeSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val dueDate: Long = System.currentTimeMillis() + 86400000 * 3, // default +3 days
    val isSmartReminderEnabled: Boolean = true,
    val reminderHour: Int = 20, // default 8 PM reminder
    val reminderMinute: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "guest",
    val taskId: Int? = null,
    val taskTitle: String? = null,
    val durationMinutes: Int = 25,
    val sessionType: String = "focus", // "focus", "break", "meditation"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reward_logs")
data class RewardLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "guest",
    val pointsEarned: Int,
    val reason: String, // e.g., "Hoàn thành 1 phiên Pomodoro", "Hoàn thành mục tiêu ngày"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_chat_messages")
data class GroupChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: String,
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GroupRoom(
    val id: String,
    val name: String,
    val creator: String,
    val activeCount: Int,
    val maxCount: Int,
    val description: String,
    val members: List<String>,
    val subject: String = "",
    val schedule: String = "",
    val focusMode: String = "25/5",
    val rules: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

data class GroupChatMessage(
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GroupMemberPresence(
    val name: String,
    val status: String,
    val focusMinutes: Int,
    val goal: String = "",
    val phaseRemainingMinutes: Int = 0,
    val cycleOffsetMinutes: Int = 0,
    val isOnline: Boolean = true,
    val lastActiveAt: Long = System.currentTimeMillis()
)
