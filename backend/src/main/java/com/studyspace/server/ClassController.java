package com.studyspace.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
class ClassController {
    private static final String CODE_CHARS="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random=new SecureRandom();
    private final JdbcTemplate db;
    private final AuthService auth;
    ClassController(JdbcTemplate db,AuthService auth) { this.db=db;this.auth=auth; }

    record NewClass(@Positive long courseId,@NotBlank @Size(max=120) String name) {}
    record JoinClass(@NotBlank @Pattern(regexp="[A-Za-z0-9]{6,12}") String code) {}
    record Announcement(@NotBlank @Size(max=2000) String body) {}

    @PostMapping("/teacher/classes")
    Map<String,Object> create(@RequestHeader("Authorization") String token,@Valid @RequestBody NewClass body) {
        var teacher=auth.teacher(token);
        if(db.queryForObject("SELECT COUNT(*) FROM courses WHERE id=? AND published=TRUE",Integer.class,body.courseId())==0)
            throw new ResponseStatusException(NOT_FOUND,"Khóa học không tồn tại hoặc chưa được xuất bản.");
        for(int attempt=0;attempt<5;attempt++) {
            String code=code();
            try {
                var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
                long now=System.currentTimeMillis();
                db.update(con->{var ps=con.prepareStatement("INSERT INTO study_classes(course_id,teacher_id,name,join_code,created_at) VALUES(?,?,?,?,?)",new String[]{"id"});ps.setLong(1,body.courseId());ps.setLong(2,teacher.id());ps.setString(3,body.name().trim());ps.setString(4,code);ps.setLong(5,now);return ps;},key);
                return Map.of("id",Objects.requireNonNull(key.getKey()).longValue(),"joinCode",code,"name",body.name().trim());
            } catch(org.springframework.dao.DuplicateKeyException ignored) { }
        }
        throw new ResponseStatusException(CONFLICT,"Không tạo được mã lớp, vui lòng thử lại.");
    }

    @GetMapping("/teacher/classes")
    List<Map<String,Object>> teacherClasses(@RequestHeader("Authorization") String token) {
        var teacher=auth.teacher(token);
        return db.queryForList("SELECT sc.id,sc.name,sc.join_code,sc.active,sc.created_at,c.id AS course_id,c.title AS course_title,COUNT(ce.student_id) AS student_count FROM study_classes sc JOIN courses c ON c.id=sc.course_id LEFT JOIN class_enrollments ce ON ce.class_id=sc.id WHERE sc.teacher_id=? AND sc.active=TRUE GROUP BY sc.id,c.id ORDER BY sc.created_at DESC",teacher.id());
    }

    @GetMapping("/teacher/classes/{id}/report")
    Map<String,Object> report(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var teacher=auth.teacher(token);
        var classes=db.queryForList("SELECT sc.id,sc.name,sc.join_code,c.id AS course_id,c.title AS course_title FROM study_classes sc JOIN courses c ON c.id=sc.course_id WHERE sc.id=? AND sc.teacher_id=? AND sc.active=TRUE",id,teacher.id());
        if(classes.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy lớp học.");
        var students=db.queryForList("""
            SELECT a.id,a.username,a.display_name,ce.enrolled_at,
              (SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=sc.course_id) AS lesson_total,
              (SELECT COUNT(*) FROM lesson_progress lp JOIN lessons l ON l.id=lp.lesson_id JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=sc.course_id AND lp.user_id=a.id) AS lesson_completed,
              (SELECT COUNT(*) FROM quiz_attempts qa JOIN chapters ch ON ch.id=qa.chapter_id WHERE ch.course_id=sc.course_id AND qa.user_id=a.id AND qa.submitted_at IS NOT NULL) AS quiz_attempts,
              (SELECT AVG(100.0*qa.score/qa.total) FROM quiz_attempts qa JOIN chapters ch ON ch.id=qa.chapter_id WHERE ch.course_id=sc.course_id AND qa.user_id=a.id AND qa.submitted_at IS NOT NULL) AS average_score,
              GREATEST(
                COALESCE((SELECT MAX(lp.completed_at) FROM lesson_progress lp JOIN lessons l ON l.id=lp.lesson_id JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=sc.course_id AND lp.user_id=a.id),0),
                COALESCE((SELECT MAX(qa.submitted_at) FROM quiz_attempts qa JOIN chapters ch ON ch.id=qa.chapter_id WHERE ch.course_id=sc.course_id AND qa.user_id=a.id AND qa.submitted_at IS NOT NULL),0)
              ) AS last_activity
            FROM class_enrollments ce JOIN accounts a ON a.id=ce.student_id JOIN study_classes sc ON sc.id=ce.class_id
            WHERE sc.id=? ORDER BY a.display_name
            """,id);
        long lessonTotal=students.stream().mapToLong(s->((Number)s.get("lesson_total")).longValue()).sum();
        long lessonCompleted=students.stream().mapToLong(s->((Number)s.get("lesson_completed")).longValue()).sum();
        long quizAttempts=students.stream().mapToLong(s->((Number)s.get("quiz_attempts")).longValue()).sum();
        var scores=students.stream().map(s->s.get("average_score")).filter(Objects::nonNull).mapToDouble(v->((Number)v).doubleValue()).toArray();
        var summary=new LinkedHashMap<String,Object>();
        summary.put("student_count",students.size());
        summary.put("lesson_total",lessonTotal);
        summary.put("lesson_completed",lessonCompleted);
        summary.put("lesson_completion_percent",lessonTotal==0?0:100.0*lessonCompleted/lessonTotal);
        summary.put("quiz_attempts",quizAttempts);
        summary.put("average_score",scores.length==0?null:Arrays.stream(scores).average().orElse(0));
        long courseId=((Number)classes.get(0).get("course_id")).longValue();
        var chapters=db.queryForList("""
            SELECT ch.id,ch.title,
              (SELECT COUNT(*) FROM lessons l WHERE l.chapter_id=ch.id) AS lesson_total,
              (SELECT COUNT(*) FROM lesson_progress lp JOIN lessons l ON l.id=lp.lesson_id WHERE l.chapter_id=ch.id AND lp.user_id IN (SELECT student_id FROM class_enrollments WHERE class_id=?)) AS lesson_completed,
              (SELECT COUNT(*) FROM quiz_attempts qa WHERE qa.chapter_id=ch.id AND qa.submitted_at IS NOT NULL AND qa.user_id IN (SELECT student_id FROM class_enrollments WHERE class_id=?)) AS quiz_attempts,
              (SELECT AVG(100.0*qa.score/qa.total) FROM quiz_attempts qa WHERE qa.chapter_id=ch.id AND qa.submitted_at IS NOT NULL AND qa.user_id IN (SELECT student_id FROM class_enrollments WHERE class_id=?)) AS average_score
            FROM chapters ch WHERE ch.course_id=? ORDER BY ch.position,ch.id
            """,id,id,id,courseId);
        var questions=db.queryForList("""
            SELECT aa.question_id,aa.prompt,COUNT(*) AS attempts,
              SUM(CASE WHEN aa.selected_option=aa.correct_option THEN 0 ELSE 1 END) AS wrong,
              100.0*SUM(CASE WHEN aa.selected_option=aa.correct_option THEN 0 ELSE 1 END)/COUNT(*) AS wrong_percent
            FROM attempt_answers aa JOIN quiz_attempts qa ON qa.id=aa.attempt_id JOIN chapters ch ON ch.id=qa.chapter_id
            WHERE ch.course_id=? AND qa.submitted_at IS NOT NULL AND qa.user_id IN (SELECT student_id FROM class_enrollments WHERE class_id=?)
            GROUP BY aa.question_id,aa.prompt ORDER BY wrong DESC,wrong_percent DESC,aa.question_id LIMIT 10
            """,courseId,id);
        var heatmap=db.queryForList("""
            SELECT a.id AS student_id,a.display_name,a.username,ch.id AS chapter_id,ch.title AS chapter_title,
              AVG(100.0*qa.score/NULLIF(qa.total,0)) AS average_score
            FROM class_enrollments ce JOIN accounts a ON a.id=ce.student_id
            CROSS JOIN chapters ch LEFT JOIN quiz_attempts qa ON qa.user_id=a.id AND qa.chapter_id=ch.id AND qa.submitted_at IS NOT NULL
            WHERE ce.class_id=? AND ch.course_id=?
            GROUP BY a.id,a.display_name,a.username,ch.id,ch.title ORDER BY a.display_name,ch.position,ch.id
            """,id,courseId);
        return Map.of("class",classes.get(0),"summary",summary,"chapters",chapters,"questions",questions,"students",students,"heatmap",heatmap);
    }

    @PostMapping("/teacher/classes/{id}/announcements")
    Map<String,Object> announce(@PathVariable long id,@RequestHeader("Authorization") String token,@Valid @RequestBody Announcement body) {
        var teacher=auth.teacher(token);
        var classes=db.queryForList("SELECT id FROM study_classes WHERE id=? AND teacher_id=? AND active=TRUE",id,teacher.id());
        if(classes.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy lớp học.");
        long createdAt=System.currentTimeMillis();
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(con->{var ps=con.prepareStatement("INSERT INTO class_announcements(class_id,author_id,body,created_at) VALUES(?,?,?,?)",new String[]{"id"});ps.setLong(1,id);ps.setLong(2,teacher.id());ps.setString(3,body.body().trim());ps.setLong(4,createdAt);return ps;},key);
        return Map.of("id",Objects.requireNonNull(key.getKey()).longValue(),"created_at",createdAt);
    }

    @GetMapping("/classes/announcements")
    List<Map<String,Object>> announcements(@RequestHeader("Authorization") String token) {
        var student=auth.require(token);
        if(!student.role().equals("STUDENT"))throw new ResponseStatusException(FORBIDDEN,"Chức năng này dành cho học viên.");
        return db.queryForList("""
            SELECT ca.id,ca.body,ca.created_at,sc.id AS class_id,sc.name AS class_name,c.title AS course_title,a.display_name AS teacher_name
            FROM class_announcements ca JOIN study_classes sc ON sc.id=ca.class_id JOIN courses c ON c.id=sc.course_id
            JOIN accounts a ON a.id=ca.author_id JOIN class_enrollments ce ON ce.class_id=sc.id
            WHERE ce.student_id=? AND sc.active=TRUE AND c.published=TRUE ORDER BY ca.created_at DESC LIMIT 20
            """,student.id());
    }

    @DeleteMapping("/teacher/classes/{id}")
    Map<String,Boolean> archive(@PathVariable long id,@RequestHeader("Authorization") String token) {
        var teacher=auth.teacher(token);
        if(db.update("UPDATE study_classes SET active=FALSE WHERE id=? AND teacher_id=? AND active=TRUE",id,teacher.id())==0)
            throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy lớp học.");
        return Map.of("ok",true);
    }

    @PostMapping("/classes/join")
    @Transactional
    Map<String,Object> join(@RequestHeader("Authorization") String token,@Valid @RequestBody JoinClass body) {
        var student=auth.require(token);
        if(!student.role().equals("STUDENT"))throw new ResponseStatusException(FORBIDDEN,"Chỉ học viên mới có thể tham gia lớp.");
        var rows=db.queryForList("SELECT sc.id,sc.name,c.title AS course_title FROM study_classes sc JOIN courses c ON c.id=sc.course_id WHERE UPPER(sc.join_code)=? AND sc.active=TRUE AND c.published=TRUE",body.code().toUpperCase(Locale.ROOT));
        if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Mã lớp không hợp lệ hoặc lớp đã đóng.");
        long classId=((Number)rows.get(0).get("id")).longValue();
        if(db.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE class_id=? AND student_id=?",Integer.class,classId,student.id())==0)
            db.update("INSERT INTO class_enrollments(class_id,student_id,enrolled_at) VALUES(?,?,?)",classId,student.id(),System.currentTimeMillis());
        return rows.get(0);
    }

    @GetMapping("/classes/mine")
    List<Map<String,Object>> mine(@RequestHeader("Authorization") String token) {
        var student=auth.require(token);
        if(!student.role().equals("STUDENT"))throw new ResponseStatusException(FORBIDDEN,"Chức năng này dành cho học viên.");
        return db.queryForList("SELECT sc.id,sc.name,c.id AS course_id,c.title AS course_title,a.display_name AS teacher_name,ce.enrolled_at FROM class_enrollments ce JOIN study_classes sc ON sc.id=ce.class_id JOIN courses c ON c.id=sc.course_id JOIN accounts a ON a.id=sc.teacher_id WHERE ce.student_id=? AND sc.active=TRUE ORDER BY ce.enrolled_at DESC",student.id());
    }

    private String code() {
        var result=new StringBuilder(8);
        for(int i=0;i<8;i++)result.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        return result.toString();
    }
}
