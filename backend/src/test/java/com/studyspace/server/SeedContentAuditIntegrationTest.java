package com.studyspace.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeedContentAuditIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired Bootstrap bootstrap;

    @Test void catalogHasCourseScopedChaptersLessonsAndExplainedQuestions() {
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM courses WHERE published=TRUE", Integer.class) >= 4);
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM chapters ch LEFT JOIN courses c ON c.id=ch.course_id WHERE c.id IS NULL", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM lessons l LEFT JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.id IS NULL", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM questions q LEFT JOIN chapters ch ON ch.id=q.chapter_id WHERE ch.id IS NULL OR q.active=TRUE AND (q.explanation IS NULL OR TRIM(q.explanation)='')", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM (SELECT c.id FROM courses c WHERE c.title IN ('Cơ sở dữ liệu','Lập trình Kotlin','Thuật toán và cấu trúc dữ liệu','Mạng máy tính và Web căn bản') AND NOT EXISTS (SELECT 1 FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=c.id GROUP BY ch.course_id HAVING COUNT(*)>=5)) missing", Integer.class));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND difficulty=1", Integer.class) > 0);
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND difficulty=2", Integer.class) > 0);
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND difficulty=3", Integer.class) > 0);
    }

    @Test void rerunningSeedDoesNotDuplicateCatalogOrDemoFocusSnapshot() throws Exception {
        int courses = db.queryForObject("SELECT COUNT(*) FROM courses", Integer.class);
        int lessons = db.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class);
        int questions = db.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
        int demoAccounts = db.queryForObject("SELECT COUNT(*) FROM accounts WHERE username='demo_focus_student'", Integer.class);
        bootstrap.run(new DefaultApplicationArguments());
        assertEquals(courses, db.queryForObject("SELECT COUNT(*) FROM courses", Integer.class));
        assertEquals(lessons, db.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class));
        assertEquals(questions, db.queryForObject("SELECT COUNT(*) FROM questions", Integer.class));
        assertEquals(demoAccounts, db.queryForObject("SELECT COUNT(*) FROM accounts WHERE username='demo_focus_student'", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM personal_study_snapshots WHERE user_id=(SELECT id FROM accounts WHERE username='demo_focus_student')", Integer.class));
        String payload = db.queryForObject("SELECT payload FROM personal_study_snapshots WHERE user_id=(SELECT id FROM accounts WHERE username='demo_focus_student')", String.class);
        assertEquals(28, new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).path("sessions").size());
    }

    @Test void algorithmSecurityCourseHasThreeSubstantiveChaptersSixLessonsAndExplainedQuestions() {
        Long courseId = db.queryForObject("SELECT id FROM courses WHERE lower(title)=lower(?)", Long.class, "Bảo mật thuật toán");
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM chapters WHERE course_id=?", Integer.class, courseId));
        assertEquals(6, db.queryForObject("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=?", Integer.class, courseId));
        assertEquals(6, db.queryForObject("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id WHERE ch.course_id=? AND q.active=TRUE", Integer.class, courseId));
        for (int difficulty = 1; difficulty <= 3; difficulty++)
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id WHERE ch.course_id=? AND q.active=TRUE AND q.difficulty=?", Integer.class, courseId, difficulty));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=? AND (LENGTH(TRIM(l.content))<200 OR lower(l.title) LIKE '%bài học:%')", Integer.class, courseId));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id WHERE ch.course_id=? AND (q.explanation IS NULL OR LENGTH(TRIM(q.explanation))<20)", Integer.class, courseId));
    }
}
