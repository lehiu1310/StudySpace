package com.studyspace.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CreatedAtMigrationIntegrationTest {
    @Autowired JdbcTemplate db;

    @Test
    void legacyRowsReceiveCreationTimesAndNewRowsGetDatabaseDefaults() {
        String suffix = Long.toString(System.nanoTime());
        db.update("INSERT INTO courses(title,description,published) VALUES(?,?,FALSE)", "created-at-" + suffix, "legacy course");
        long courseId = db.queryForObject("SELECT id FROM courses WHERE title=?", Long.class, "created-at-" + suffix);
        db.update("INSERT INTO chapters(course_id,title,position) VALUES(?,?,0)", courseId, "Legacy chapter");
        long chapterId = db.queryForObject("SELECT id FROM chapters WHERE course_id=?", Long.class, courseId);
        db.update("INSERT INTO lessons(chapter_id,title,content,position) VALUES(?,?,?,0)", chapterId, "Legacy lesson", "Legacy content");
        long lessonId = db.queryForObject("SELECT id FROM lessons WHERE chapter_id=?", Long.class, chapterId);

        db.update("UPDATE courses SET created_at=0 WHERE id=?", courseId);
        db.update("UPDATE chapters SET created_at=0 WHERE id=?", chapterId);
        db.update("UPDATE lessons SET created_at=0 WHERE id=?", lessonId);
        for (String table : new String[]{"courses", "chapters", "lessons"}) {
            db.update("UPDATE " + table + " SET created_at=CAST(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 AS BIGINT) WHERE created_at=0");
        }

        assertTrue(db.queryForObject("SELECT created_at FROM courses WHERE id=?", Long.class, courseId) > 0);
        assertTrue(db.queryForObject("SELECT created_at FROM chapters WHERE id=?", Long.class, chapterId) > 0);
        assertTrue(db.queryForObject("SELECT created_at FROM lessons WHERE id=?", Long.class, lessonId) > 0);
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM lessons WHERE id=? AND content='Legacy content'", Integer.class, lessonId) == 1);

        db.update("INSERT INTO courses(title,description,published) VALUES(?,?,FALSE)", "created-at-default-" + suffix, "new course");
        assertTrue(db.queryForObject("SELECT created_at FROM courses WHERE title=?", Long.class, "created-at-default-" + suffix) > 0);
    }
}
