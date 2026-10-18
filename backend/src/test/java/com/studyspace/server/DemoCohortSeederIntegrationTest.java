package com.studyspace.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DemoCohortSeederIntegrationTest {
    @Autowired DemoCohortSeeder seeder;
    @Autowired JdbcTemplate db;

    @Test
    @Transactional
    void createsRichIsolatedCohortAndRerunsWithoutDuplicates() {
        int originalAccounts = db.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        seeder.seed();
        int courses = count("SELECT COUNT(*) FROM courses WHERE title='Cơ sở dữ liệu · Lớp thực hành mẫu'");
        int chapters = count("SELECT COUNT(*) FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu'");
        int lessons = count("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu'");
        int questions = count("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu'");
        int enrollments = count("SELECT COUNT(*) FROM class_enrollments ce JOIN study_classes sc ON sc.id=ce.class_id WHERE sc.join_code='DEMO2026'");
        int attempts = count("SELECT COUNT(*) FROM quiz_attempts qa JOIN accounts a ON a.id=qa.user_id WHERE a.username LIKE 'demo_cohort_%' AND qa.submitted_at IS NOT NULL");
        assertEquals(1, count("SELECT COUNT(*) FROM accounts WHERE username='demo_cohort_teacher' AND role='TEACHER'"));
        assertEquals(28, count("SELECT COUNT(*) FROM accounts WHERE username LIKE 'demo_cohort_%' AND role='STUDENT'"));
        assertEquals(3, count("SELECT COUNT(*) FROM courses WHERE created_by=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(18, count("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.created_by=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(30, count("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.created_by=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(2, count("SELECT COUNT(*) FROM study_classes WHERE teacher_id=(SELECT id FROM accounts WHERE username='demo_cohort_teacher') AND active=TRUE"));
        assertEquals(56, count("SELECT COUNT(*) FROM class_enrollments ce JOIN study_classes sc ON sc.id=ce.class_id WHERE sc.teacher_id=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(1, courses);
        assertEquals(3, chapters);
        assertEquals(6, lessons);
        assertEquals(18, questions);
        assertEquals(28, enrollments);
        assertEquals(306, attempts);
        assertEquals(4, count("SELECT COUNT(*) FROM course_messages WHERE course_id=(SELECT id FROM courses WHERE title='Cơ sở dữ liệu · Lớp thực hành mẫu')"));
        assertEquals(3, count("SELECT COUNT(*) FROM courses WHERE created_by=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(18, count("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.created_by=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(30, count("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.created_by=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(2, count("SELECT COUNT(*) FROM study_classes WHERE teacher_id=(SELECT id FROM accounts WHERE username='demo_cohort_teacher') AND active=TRUE"));
        assertEquals(56, count("SELECT COUNT(*) FROM class_enrollments ce JOIN study_classes sc ON sc.id=ce.class_id WHERE sc.teacher_id=(SELECT id FROM accounts WHERE username='demo_cohort_teacher')"));
        assertEquals(0, count("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu' AND q.difficulty NOT BETWEEN 1 AND 3"));
        assertTrue(count("SELECT COUNT(*) FROM quiz_attempts qa JOIN accounts a ON a.id=qa.user_id JOIN chapters ch ON ch.id=qa.chapter_id WHERE a.username LIKE 'demo_cohort_%' AND ch.title='Chuẩn hóa dữ liệu' AND qa.submitted_at IS NOT NULL AND qa.score < 4") > 0);
        assertEquals(0, count("SELECT COUNT(*) FROM accounts a WHERE a.username LIKE 'demo_cohort_%' AND a.role='STUDENT' AND a.id NOT IN (SELECT user_id FROM personal_study_snapshots)"));
        assertEquals(originalAccounts + 29, count("SELECT COUNT(*) FROM accounts"));

        seeder.seed();
        assertEquals(courses, count("SELECT COUNT(*) FROM courses WHERE title='Cơ sở dữ liệu · Lớp thực hành mẫu'"));
        assertEquals(chapters, count("SELECT COUNT(*) FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu'"));
        assertEquals(lessons, count("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu'"));
        assertEquals(questions, count("SELECT COUNT(*) FROM questions q JOIN chapters ch ON ch.id=q.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.title='Cơ sở dữ liệu · Lớp thực hành mẫu'"));
        assertEquals(enrollments, count("SELECT COUNT(*) FROM class_enrollments ce JOIN study_classes sc ON sc.id=ce.class_id WHERE sc.join_code='DEMO2026'"));
        assertEquals(attempts, count("SELECT COUNT(*) FROM quiz_attempts qa JOIN accounts a ON a.id=qa.user_id WHERE a.username LIKE 'demo_cohort_%' AND qa.submitted_at IS NOT NULL"));
        assertEquals(4, count("SELECT COUNT(*) FROM course_messages WHERE course_id=(SELECT id FROM courses WHERE title='Cơ sở dữ liệu · Lớp thực hành mẫu')"));
    }

    private int count(String sql) { return db.queryForObject(sql, Integer.class); }
}
