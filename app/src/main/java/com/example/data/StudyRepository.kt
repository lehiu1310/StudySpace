package com.example.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudyRepository(private val studyDao: StudyDao) {
    suspend fun getUser(username: String): User? = studyDao.getUser(username)
    suspend fun insertUser(user: User) = studyDao.insertUser(user)
    suspend fun upgradePasswordHash(username: String, expectedHash: String, newHash: String): Boolean =
        studyDao.updatePasswordHashIfUnchanged(username, expectedHash, newHash) == 1

    fun getTasksForUser(userId: String): Flow<List<StudyTask>> = studyDao.getTasksByUserId(userId)
    fun getSessionsForUser(userId: String): Flow<List<FocusSession>> = studyDao.getSessionsByUserId(userId)
    fun getRewardsForUser(userId: String): Flow<List<RewardLog>> = studyDao.getRewardsByUserId(userId)
    fun getTotalPointsForUser(userId: String): Flow<Int?> = studyDao.getTotalPointsFlow(userId)
    fun getGroupMessages(roomId: String): Flow<List<GroupChatMessage>> {
        return studyDao.getGroupMessages(roomId).map { rows ->
            rows.map { row ->
                GroupChatMessage(
                    sender = row.sender,
                    message = row.message,
                    timestamp = row.timestamp
                )
            }
        }
    }

    suspend fun getTaskById(id: Int): StudyTask? = studyDao.getTaskById(id)

    fun getSessionsSince(userId: String, since: Long): Flow<List<FocusSession>> = studyDao.getSessionsSince(userId, since)

    suspend fun insertTask(task: StudyTask) = studyDao.insertTask(task)

    suspend fun deleteTask(task: StudyTask) = studyDao.deleteTask(task)

    suspend fun deleteTaskById(id: Int) = studyDao.deleteTaskById(id)

    suspend fun insertSession(session: FocusSession) = studyDao.insertSession(session)

    suspend fun insertReward(log: RewardLog) = studyDao.insertReward(log)

    suspend fun countGroupMessages(roomId: String): Int = studyDao.countGroupMessages(roomId)

    suspend fun insertGroupMessage(roomId: String, sender: String, message: String, timestamp: Long = System.currentTimeMillis()) {
        studyDao.insertGroupMessage(
            GroupChatEntity(
                roomId = roomId,
                sender = sender,
                message = message,
                timestamp = timestamp
            )
        )
        studyDao.trimGroupMessages(roomId)
    }

    suspend fun resetDatabaseForUser(userId: String) {
        studyDao.clearTasksForUser(userId)
        studyDao.clearSessionsForUser(userId)
        studyDao.clearRewardsForUser(userId)
    }

    fun exportToCSV(context: Context, tasks: List<StudyTask>, sessions: List<FocusSession>): String? {
        return try {
            val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getReportDir(context), "LichSuHocTap_$dateTime.csv")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            FileWriter(file).use { writer ->
                writer.append("--- MỤC TIÊU HỌC TẬP ---\n")
                writer.append("Mã,Tiêu đề,Chuyên mục,Thời lượng mục tiêu (phút),Đã học (giây),Hoàn thành,Hạn chót\n")
                tasks.forEach { task ->
                    writer.append("${task.id},")
                    writer.append("\"${task.title.escapeCsv()}\",")
                    writer.append("\"${task.category.escapeCsv()}\",")
                    writer.append("${task.targetMinutes},")
                    writer.append("${task.elapsedTimeSeconds},")
                    writer.append("${if (task.isCompleted) "Rồi" else "Chưa"},")
                    writer.append("${dateFormat.format(Date(task.dueDate))}\n")
                }

                writer.append("\n--- PHIÊN TẬP TRUNG / THIỀN ĐỊNH ---\n")
                writer.append("Mã,Mã mục tiêu,Tên mục tiêu,Phút,Loại phiên,Thời gian\n")
                sessions.forEach { session ->
                    writer.append("${session.id},")
                    writer.append("${session.taskId ?: ""},")
                    writer.append("\"${(session.taskTitle ?: "Tự do").escapeCsv()}\",")
                    writer.append("${session.durationMinutes},")
                    writer.append("${session.sessionType},")
                    writer.append("${dateFormat.format(Date(session.timestamp))}\n")
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToPDF(context: Context, tasks: List<StudyTask>, sessions: List<FocusSession>): String? {
        var document: PdfDocument? = null
        return try {
            val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getReportDir(context), "BaoCaoHocTap_$dateTime.pdf")
            val pdf = PdfDocument()
            document = pdf
            val writer = PdfReportWriter(pdf)
            val exportDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

            writer.title("BÁO CÁO TIẾN ĐỘ HỌC TẬP")
            writer.body("Ngày xuất báo cáo: $exportDate")
            writer.space(12)

            writer.section("1. Tổng quan")
            val focusSessions = sessions.filter { it.sessionType == "focus" }
            val completedTasks = tasks.count { it.isCompleted }
            val totalFocusMinutes = focusSessions.sumOf { it.durationMinutes }
            writer.body("Mục tiêu hoàn thành: $completedTasks/${tasks.size}")
            writer.body("Tổng số phiên tập trung: ${focusSessions.size}")
            writer.body("Tổng thời lượng tập trung: $totalFocusMinutes phút")
            writer.space(10)

            writer.section("2. Mục tiêu học tập")
            if (tasks.isEmpty()) {
                writer.body("Chưa có mục tiêu học tập.")
            } else {
                tasks.forEachIndexed { index, task ->
                    val elapsedMinutes = task.elapsedTimeSeconds / 60
                    val status = if (task.isCompleted) "Hoàn thành" else "Đang học"
                    writer.body("${index + 1}. ${task.title}")
                    writer.subtle("${task.category} | $elapsedMinutes/${task.targetMinutes} phút | $status")
                }
            }
            writer.space(10)

            writer.section("3. Lịch sử phiên học")
            val sessionDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            if (sessions.isEmpty()) {
                writer.body("Chưa có phiên học nào.")
            } else {
                sessions.take(40).forEachIndexed { index, session ->
                    val title = session.taskTitle ?: "Tập trung tự do"
                    writer.body("${index + 1}. $title")
                    writer.subtle("${sessionDateFormat.format(Date(session.timestamp))} | ${session.durationMinutes} phút | ${session.sessionType}")
                }
                if (sessions.size > 40) {
                    writer.subtle("Còn ${sessions.size - 40} phiên khác được lưu trong cơ sở dữ liệu.")
                }
            }

            writer.finish()
            FileOutputStream(file).use { pdf.writeTo(it) }
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("StudySpaceFocusPdf", "Failed to create or save focus report", e)
            null
        } finally {
            runCatching { document?.close() }
        }
    }

    private fun getReportDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        check(dir.isDirectory || dir.mkdirs()) { "Không tạo được thư mục lưu báo cáo." }
        return dir
    }

    private fun String.escapeCsv(): String = replace("\"", "\"\"")

    private class PdfReportWriter(private val document: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 42f
        private var pageNumber = 0
        private var y = margin
        private var page = newPage()

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 45, 64)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(19, 101, 148)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val subtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(99, 105, 115)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }

        fun title(text: String) {
            ensureSpace(36f)
            page.canvas.drawText(text, margin, y, titlePaint)
            y += 30f
        }

        fun section(text: String) {
            ensureSpace(32f)
            page.canvas.drawText(text, margin, y, sectionPaint)
            y += 24f
        }

        fun body(text: String) = line(text, bodyPaint, 17f)

        fun subtle(text: String) = line(text, subtlePaint, 15f)

        fun space(amount: Int) {
            ensureSpace(amount.toFloat())
            y += amount
        }

        fun finish() {
            document.finishPage(page)
        }

        private fun line(text: String, paint: Paint, lineHeight: Float) {
            val maxWidth = pageWidth - margin * 2
            wrapText(text, paint, maxWidth).forEach { line ->
                ensureSpace(lineHeight)
                page.canvas.drawText(line, margin, y, paint)
                y += lineHeight
            }
        }

        private fun ensureSpace(required: Float) {
            if (y + required <= pageHeight - margin) return
            document.finishPage(page)
            page = newPage()
            y = margin
        }

        private fun newPage(): PdfDocument.Page {
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            return document.startPage(pageInfo)
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (paint.measureText(text) <= maxWidth) return listOf(text)
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) lines += current
                    current = word
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }
    }
}
