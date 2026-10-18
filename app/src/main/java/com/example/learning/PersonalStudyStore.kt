package com.example.learning

import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class PersonalStudyStore(private val db: StudyDatabase) {
    suspend fun capture(user: String): JSONObject = db.withTransaction {
        val plans = db.studyDao().getTasksByUserId(user).first().sortedBy { it.id }
        val sessions = db.studyDao().getSessionsByUserId(user).first().sortedBy { it.id }
        JSONObject().put("plans", JSONArray().apply {
            plans.forEach { t -> put(JSONObject().put("id",t.id).put("title",t.title).put("category",t.category)
                .put("description",t.description).put("targetMinutes",t.targetMinutes).put("elapsedTimeSeconds",t.elapsedTimeSeconds)
                .put("isCompleted",t.isCompleted).put("dueDate",t.dueDate).put("isSmartReminderEnabled",t.isSmartReminderEnabled)
                .put("reminderHour",t.reminderHour).put("reminderMinute",t.reminderMinute).put("createdAt",t.createdAt)
                .put("completedAt",t.completedAt ?: 0L)) }
        }).put("sessions", JSONArray().apply {
            sessions.forEach { s -> put(JSONObject().put("id",s.id).put("taskId",s.taskId ?: JSONObject.NULL)
                .put("taskTitle",s.taskTitle ?: JSONObject.NULL).put("durationMinutes",s.durationMinutes)
                .put("sessionType",s.sessionType).put("timestamp",s.timestamp)) }
        })
    }

    data class Restored(val oldTaskIds: List<Int>, val tasks: List<StudyTask>, val appliedHash: String)

    suspend fun replace(user: String, remote: JSONObject, expectedLocalHash: String): Restored = db.withTransaction {
        require(hash(capture(user)) == expectedLocalHash) { "Dữ liệu trên thiết bị vừa thay đổi. Hãy đồng bộ lại." }
        val plans = remote.getJSONArray("plans").objects()
        val sessions = remote.getJSONArray("sessions").objects()
        require(plans.size <= 5000 && sessions.size <= 20000)
        require(plans.map { it.getInt("id") }.distinct().size == plans.size)
        require(sessions.map { it.getInt("id") }.distinct().size == sessions.size)
        val parsed = plans.map { t ->
            require(t.getInt("id") > 0 && t.getString("title").isNotBlank())
            require(t.getInt("targetMinutes") in 1..1440 && t.getInt("elapsedTimeSeconds") >= 0)
            require(t.getInt("reminderHour") in 0..23 && t.getInt("reminderMinute") in 0..59)
            val rawCompletedAt = t.optLong("completedAt")
            require(rawCompletedAt >= 0L)
            val completedAt = rawCompletedAt.takeIf { it > 0L }
            require(t.getBoolean("isCompleted") || completedAt == null)
            t.getInt("id") to StudyTask(userId=user, title=t.getString("title"), category=t.getString("category"),
                description=t.getString("description"), targetMinutes=t.getInt("targetMinutes"), elapsedTimeSeconds=t.getInt("elapsedTimeSeconds"),
                isCompleted=t.getBoolean("isCompleted"), dueDate=t.getLong("dueDate"), isSmartReminderEnabled=t.getBoolean("isSmartReminderEnabled"),
                reminderHour=t.getInt("reminderHour"), reminderMinute=t.getInt("reminderMinute"), createdAt=t.getLong("createdAt"),
                completedAt=completedAt)
        }
        val parsedSessions = sessions.map { s ->
            require(s.getInt("id") > 0 && s.getInt("durationMinutes") in 1..1440)
            require(s.getString("sessionType") in setOf("focus","break","meditation"))
            (if(s.isNull("taskId")) null else s.getInt("taskId")) to FocusSession(userId=user,
                taskTitle=if(s.isNull("taskTitle")) null else s.getString("taskTitle"), durationMinutes=s.getInt("durationMinutes"),
                sessionType=s.getString("sessionType"), timestamp=s.getLong("timestamp"))
        }
        val dao = db.studyDao()
        val oldIds = dao.getTasksByUserId(user).first().map { it.id }
        dao.clearSessionsForUser(user)
        dao.clearTasksForUser(user)
        val idMap = mutableMapOf<Int,Int>()
        val newTasks = parsed.map { (oldId, task) ->
            val id = dao.insertTask(task).toInt()
            idMap[oldId] = id
            task.copy(id=id)
        }
        parsedSessions.forEach { (oldTaskId, session) -> dao.insertSession(session.copy(taskId=idMap[oldTaskId])) }
        Restored(oldIds, newTasks, hash(capture(user)))
    }

    companion object {
        fun normalize(value: JSONObject): JSONObject {
            val normalized = JSONObject(value.toString())
            val plans = normalized.getJSONArray("plans")
            for (index in 0 until plans.length()) {
                val plan = plans.getJSONObject(index)
                if (!plan.has("completedAt")) plan.put("completedAt", 0L)
            }
            return normalized
        }

        fun hash(value: JSONObject): String {
            fun canonical(item: Any?): String = when (item) {
                is JSONObject -> item.keys().asSequence().toList().sorted().joinToString(",","{","}") { JSONObject.quote(it) + ":" + canonical(item.get(it)) }
                is JSONArray -> (0 until item.length()).joinToString(",","[","]") { canonical(item.get(it)) }
                is String -> JSONObject.quote(item)
                null, JSONObject.NULL -> "null"
                else -> item.toString()
            }
            return MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
