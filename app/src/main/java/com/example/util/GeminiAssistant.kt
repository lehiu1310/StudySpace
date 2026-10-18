package com.example.util

import com.example.data.StudyTask

object GeminiAssistant {
    fun buildOfflineChallenge(request: StudySkillRequest): StudyChallenge {
        val goal = request.goal.ifBlank { "mục tiêu học hiện tại" }
        val category = request.category.ifBlank { "Khác" }
        val levelHint = when (request.level) {
            "Mất gốc" -> "bắt đầu bằng phần nền tảng nhỏ nhất, tránh nhảy thẳng vào bài khó"
            "Cơ bản" -> "củng cố khái niệm lõi rồi mới luyện áp dụng"
            "Khá" -> "tăng độ khó bằng bài tổng hợp và tự giải thích lại"
            else -> "học theo nhịp vừa sức, có kiểm tra ngay cuối phiên"
        }
        val weaknessFix = when (request.weakness) {
            "Hay trì hoãn" -> "đặt mốc 5 phút đầu thật dễ để phá quán tính"
            "Dễ quên" -> "dùng truy hồi chủ động: tự viết lại trước khi xem đáp án"
            "Không hiểu gốc" -> "vẽ lại khái niệm bằng ví dụ đơn giản trước khi làm bài"
            "Sai khi làm bài" -> "ghi log lỗi sai theo mẫu: giả thiết, bước sai, cách sửa"
            else -> "giữ nhiệm vụ nhỏ, có đầu ra nhìn thấy được"
        }
        val dataSignal = when {
            request.focusSessionCount == 0 -> "Bạn chưa có phiên tập trung nào, nên kế hoạch cần ngắn và dễ bắt đầu."
            request.weeklyFocusMinutes < 60 -> "Tuần này thời lượng tập trung còn thấp, nên ưu tiên một phiên gọn nhưng có sản phẩm."
            request.openTaskCount > request.completedTaskCount + 3 -> "Bạn đang có khá nhiều mục tiêu mở, nên cần chốt một đầu việc thay vì mở thêm."
            else -> "Dữ liệu học tập đang đủ để tăng dần độ khó mà vẫn kiểm soát được."
        }
        val exercise = when (category) {
            "Lập trình" -> "Với '$goal', viết một test hoặc ví dụ input/output trước, rồi hoàn thành phần code nhỏ nhất chạy được. Cuối phiên ghi lại một lỗi và cách sửa."
            "Toán" -> "Giải ba bài cùng dạng với '$goal': một bài mẫu, một bài tự giải và một bài biến thể. Ghi rõ giả thiết, công thức và bước dễ sai."
            "Ngoại ngữ" -> "Từ '$goal', chọn tám cụm từ, viết bốn câu của riêng bạn rồi che nghĩa để tự nhớ lại."
            else -> "Biến '$goal' thành sơ đồ ba nhánh: khái niệm, ví dụ và câu hỏi còn mơ hồ. Tự giảng lại trong 90 giây."
        }
        val split = (request.timeBudgetMinutes - 5).coerceAtLeast(10)
        return StudyChallenge(
            title = "$category Sprint ${request.timeBudgetMinutes} phút",
            diagnosis = "$dataSignal Với trình độ ${request.level.lowercase()}, bạn nên $levelHint.",
            tip = "Điểm yếu hiện tại là '${request.weakness}', nên hãy $weaknessFix. Đo bằng sản phẩm cuối phiên, không chỉ bằng cảm giác hiểu.",
            microPlan = listOf("0-5p: mở mục tiêu '$goal' và ghi đầu ra cần có", "5-${split}p: làm bài chính theo cách ${request.learningStyle.lowercase()}", "${split}-${request.timeBudgetMinutes}p: tự chấm và ghi việc cần học tiếp"),
            exercise = exercise,
            answerChecklist = listOf("Có sản phẩm cụ thể", "Ghi được một lỗi hoặc điểm yếu thật", "Nêu được bước tiếp theo trong một câu"),
            estimatedMinutes = request.timeBudgetMinutes,
            difficulty = if (request.level == "Mất gốc" || request.level == "Cơ bản") "Vừa sức" else if (request.level == "Khá") "Khó" else "Vừa sức",
            nextAction = "Lưu thành mục tiêu nhỏ rồi chạy một phiên Focus ${request.timeBudgetMinutes} phút."
        )
    }

    fun offlinePlan(category: String): List<StudyTask> {
        val defaults = when (category) {
            "Lập trình" -> listOf(
                Triple("Học Kotlin qua bài tập nhỏ", "Viết chương trình xử lý danh sách và kiểm thử các trường hợp biên.", 30),
                Triple("Xây giao diện Compose", "Tạo màn hình danh sách có trạng thái tải, rỗng và lỗi.", 45),
                Triple("Lưu dữ liệu bằng Room", "Tạo Entity, DAO và truy vấn dữ liệu theo tài khoản.", 45)
            )
            "Toán" -> listOf(
                Triple("Ôn khái niệm trọng tâm", "Tóm tắt định nghĩa và tự tạo một ví dụ.", 25),
                Triple("Giải bài có hướng dẫn", "Giải hai bài mẫu, ghi lại lý do chọn từng bước.", 35),
                Triple("Tự luyện và chữa lỗi", "Làm ba bài không xem lời giải rồi phân loại lỗi.", 30)
            )
            "Ngoại ngữ" -> listOf(
                Triple("Ôn cụm từ theo chủ đề", "Tạo thẻ từ có ví dụ trong ngữ cảnh.", 20),
                Triple("Luyện nghe và nhắc lại", "Nghe đoạn ngắn, ghi từ khóa rồi shadowing hai lượt.", 25),
                Triple("Viết và tự sửa đoạn ngắn", "Viết đoạn văn rồi kiểm tra từ vựng và ngữ pháp.", 30)
            )
            else -> listOf(
                Triple("Chọn ý chính cần nhớ", "Đọc một mục tài liệu và ghi ba ý cốt lõi.", 25),
                Triple("Tạo sơ đồ kiến thức", "Nối khái niệm với ví dụ và câu hỏi còn mở.", 30),
                Triple("Tự kiểm tra kiến thức", "Đóng tài liệu, trả lời năm câu hỏi tự tạo.", 20)
            )
        }
        return defaults.map { (title, description, minutes) ->
            StudyTask(title = title, category = category, description = description, targetMinutes = minutes)
        }
    }
}

data class StudyChallenge(
    val title: String,
    val tip: String,
    val exercise: String,
    val diagnosis: String = "",
    val microPlan: List<String> = emptyList(),
    val answerChecklist: List<String> = emptyList(),
    val estimatedMinutes: Int = 25,
    val difficulty: String = "Vừa sức",
    val nextAction: String = ""
)

data class StudySkillRequest(
    val category: String,
    val goal: String,
    val level: String = "Cơ bản",
    val weakness: String = "Hay trì hoãn",
    val learningStyle: String = "Thực hành",
    val timeBudgetMinutes: Int = 25,
    val deadline: String = "Hôm nay",
    val weeklyFocusMinutes: Int = 0,
    val openTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val focusSessionCount: Int = 0,
    val recentTaskTitles: List<String> = emptyList()
)
