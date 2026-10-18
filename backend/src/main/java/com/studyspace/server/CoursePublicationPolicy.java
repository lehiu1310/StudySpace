package com.studyspace.server;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class CoursePublicationPolicy {
    private final JdbcTemplate db;

    CoursePublicationPolicy(JdbcTemplate db) { this.db = db; }

    void validate(long courseId, String title, String description) {
        var courses = db.queryForList("SELECT title,description FROM courses WHERE id=?", courseId);
        if (courses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học.");
        if (isPlaceholder(title) || isPlaceholder(description))
            reject("Tên hoặc mô tả khóa học vẫn là nội dung mẫu. Hãy bổ sung nội dung thật trước khi xuất bản.");

        List<java.util.Map<String,Object>> chapters = db.queryForList(
            "SELECT id,title FROM chapters WHERE course_id=? ORDER BY position,id", courseId);
        if (chapters.isEmpty()) reject("Khóa học cần có ít nhất một chương và bài học trước khi xuất bản.");
        for (var chapter : chapters) {
            long chapterId = ((Number) chapter.get("id")).longValue();
            String chapterTitle = String.valueOf(chapter.get("title"));
            if (isPlaceholder(chapterTitle))
                reject("Chương '" + chapterTitle + "' đang dùng tiêu đề mẫu. Hãy đổi sang tên chương cụ thể.");
            var lessons = db.queryForList("SELECT title,content FROM lessons WHERE chapter_id=? ORDER BY position,id", chapterId);
            if (lessons.isEmpty()) reject("Chương '" + chapterTitle + "' chưa có bài học.");
            for (var lesson : lessons) {
                String lessonTitle = Objects.toString(lesson.get("title"), "");
                String content = Objects.toString(lesson.get("content"), "");
                if (isPlaceholder(lessonTitle))
                    reject("Bài học '" + lessonTitle + "' đang dùng tiêu đề mẫu. Hãy đặt tên bài cụ thể.");
                if (content.isBlank() || isPlaceholder(content))
                    reject("Bài học '" + lessonTitle + "' chưa có nội dung thật.");
            }
        }
    }

    static boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
            .toLowerCase(Locale.ROOT).trim();
        return normalized.contains("lorem") || normalized.contains("placeholder") || normalized.equals("todo")
            || normalized.contains("chuong tiep theo trong lo trinh")
            || normalized.equals("chu de hoc tap") || normalized.equals("noi dung mau")
            || normalized.equals("untitled") || normalized.matches("test(?:\\s*\\d+)?")
            || normalized.matches(".*\\b(bai hoc|ten bai|ten chuong)\\s*:?\\s*$");
    }

    private static void reject(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
