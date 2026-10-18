package com.studyspace.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/courses/{courseId}/discussion")
class DiscussionController {
    final JdbcTemplate db; final AuthService auth;
    DiscussionController(JdbcTemplate db,AuthService auth) {this.db=db;this.auth=auth;}
    void available(long id) {
        if(db.queryForObject("SELECT COUNT(*) FROM courses WHERE id=? AND published=TRUE",Integer.class,id)==0)
            throw new ResponseStatusException(NOT_FOUND,"Môn học không còn mở.");
    }
    void member(long courseId,AuthService.Account user) {
        String sql=user.role().equals("TEACHER")
            ? "SELECT COUNT(*) FROM study_classes WHERE course_id=? AND teacher_id=? AND active=TRUE"
            : "SELECT COUNT(*) FROM class_enrollments ce JOIN study_classes sc ON sc.id=ce.class_id WHERE sc.course_id=? AND ce.student_id=? AND sc.active=TRUE";
        if(db.queryForObject(sql,Integer.class,courseId,user.id())==0)
            throw new ResponseStatusException(FORBIDDEN,"Bạn cần tham gia lớp học của môn này để xem thảo luận.");
    }
    @GetMapping @Transactional
    Map<String,Object> read(@PathVariable long courseId,@RequestHeader("Authorization") String token) {
        var user=auth.require(token);available(courseId);member(courseId,user);
        db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE",Long.class,user.id());
        long now=System.currentTimeMillis();
        if(db.update("UPDATE course_presence SET seen_at=? WHERE course_id=? AND user_id=?",now,courseId,user.id())==0)
            db.update("INSERT INTO course_presence(course_id,user_id,seen_at) VALUES(?,?,?)",courseId,user.id(),now);
        var messages=db.queryForList("SELECT m.id,m.user_id,a.username,COALESCE(NULLIF(a.display_name,''),a.username) AS display_name,a.role,m.body,m.sent_at FROM course_messages m JOIN accounts a ON a.id=m.user_id WHERE m.course_id=? ORDER BY m.id DESC LIMIT 100",courseId);
        Collections.reverse(messages);
        var online=db.queryForList("SELECT a.username FROM course_presence p JOIN accounts a ON a.id=p.user_id WHERE p.course_id=? AND p.seen_at>? AND a.enabled=TRUE ORDER BY a.username",courseId,now-35000);
        return Map.of("messages",messages,"online",online);
    }
    record Message(@NotBlank @Size(max=2000) String body) {}
    @PostMapping @Transactional
    Map<String,Boolean> send(@PathVariable long courseId,@RequestHeader("Authorization") String token,@Valid @RequestBody Message message) {
        var user=auth.require(token);available(courseId);member(courseId,user);
        db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE",Long.class,user.id());
        long now=System.currentTimeMillis();
        var last=db.queryForObject("SELECT MAX(sent_at) FROM course_messages WHERE user_id=?",Long.class,user.id());
        if(last!=null && now-last<1000)throw new ResponseStatusException(TOO_MANY_REQUESTS,"Hãy chờ một chút trước khi gửi tiếp.");
        db.update("INSERT INTO course_messages(course_id,user_id,body,sent_at) VALUES(?,?,?,?)",courseId,user.id(),message.body().trim(),now);
        return Map.of("ok",true);
    }
    @DeleteMapping
    void leave(@PathVariable long courseId,@RequestHeader("Authorization") String token) {
        var user=auth.require(token);db.update("DELETE FROM course_presence WHERE course_id=? AND user_id=?",courseId,user.id());
    }
}
