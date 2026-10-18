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
@RequestMapping("/api/admin")
class AdminController {
    final AuthService auth;
    final JdbcTemplate db;
    final CoursePublicationPolicy publicationPolicy;
    AdminController(AuthService auth,JdbcTemplate db,CoursePublicationPolicy publicationPolicy) {
        this.auth=auth;this.db=db;this.publicationPolicy=publicationPolicy;
    }
    record Course(@NotBlank @Size(max=160) String title,@NotNull @Size(max=10000) String description,boolean published) {}
    record Chapter(@Positive long courseId,@NotBlank @Size(max=160) String title,@Min(0) int position) {}
    record Lesson(@Positive long chapterId,@NotBlank @Size(max=160) String title,@NotBlank @Size(max=100000) String content,@NotNull @Pattern(regexp="^$|https://[^\\s]+|/materials/[0-9a-fA-F-]{36}\\.(pdf|ppt|pptx)") @Size(max=2000) String materialUrl,@Min(0) int position) {}
    record Question(@Positive long chapterId,@NotBlank @Size(max=10000) String prompt,@NotNull @Size(min=4,max=4) List<@NotBlank @Size(max=1000) String> options,@Min(0) @Max(3) int correctOption,@NotBlank @Size(max=10000) String explanation,@Min(1) @Max(3) int difficulty) {}
    record UserState(boolean enabled) {}
    record UserRole(@NotBlank @Pattern(regexp="STUDENT|TEACHER") String role) {}
    long insert(String table, String columns, Object... values) {
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(con->{var ps=con.prepareStatement("INSERT INTO "+table+"("+columns+") VALUES("+String.join(",",Collections.nCopies(values.length,"?"))+")",new String[]{"id"});for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);return ps;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private void audit(long actorId,String action,String targetType,long targetId,String detail) {
        db.update("INSERT INTO admin_audit_log(actor_id,action,target_type,target_id,detail) VALUES(?,?,?,?,?)",
            actorId,action,targetType,targetId,detail==null?"":detail.substring(0,Math.min(500,detail.length())));
    }
    @GetMapping("/content") Map<String,Object> content(@RequestHeader("Authorization") String t) {
        auth.admin(t); return Map.of("courses",db.queryForList("SELECT * FROM courses ORDER BY id"),"chapters",db.queryForList("SELECT * FROM chapters ORDER BY course_id,position"),"lessons",db.queryForList("SELECT * FROM lessons ORDER BY chapter_id,position"),"questions",db.queryForList("SELECT * FROM questions ORDER BY id"));
    }
    @PostMapping("/courses") Map<String,Long> createCourse(@RequestHeader("Authorization") String t,@Valid @RequestBody Course c) {
        var actor=auth.admin(t);
        if(c.published())throw new ResponseStatusException(BAD_REQUEST,"Hãy tạo bản nháp, thêm chương và bài học có nội dung thật rồi mới xuất bản.");
        long id=insert("courses","title,description,published",c.title(),c.description(),c.published());audit(actor.id(),"CREATE_COURSE","COURSE",id,c.title());return Map.of("id",id);
    }
    @PutMapping("/courses/{id}") void course(@PathVariable long id,@RequestHeader("Authorization") String t,@Valid @RequestBody Course c) {
        var actor=auth.admin(t);if(c.published())publicationPolicy.validate(id,c.title(),c.description());
        if(db.update("UPDATE courses SET title=?,description=?,published=? WHERE id=?",c.title(),c.description(),c.published(),id)==0)
            throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy khóa học.");
        audit(actor.id(),c.published()?"PUBLISH_COURSE":"UPDATE_COURSE","COURSE",id,c.title());
    }
    @DeleteMapping("/courses/{id}") void archive(@PathVariable long id,@RequestHeader("Authorization") String t) {var actor=auth.admin(t);if(db.update("UPDATE courses SET published=FALSE WHERE id=?",id)>0)audit(actor.id(),"UNPUBLISH_COURSE","COURSE",id,"");}
    @PostMapping("/chapters") Map<String,Long> chapter(@RequestHeader("Authorization") String t,@Valid @RequestBody Chapter c) {var a=auth.admin(t);long id=insert("chapters","course_id,title,position",c.courseId(),c.title(),c.position());audit(a.id(),"CREATE_CHAPTER","CHAPTER",id,c.title());return Map.of("id",id);}
    @PutMapping("/chapters/{id}") void chapter(@PathVariable long id,@RequestHeader("Authorization") String t,@Valid @RequestBody Chapter c) {var a=auth.admin(t);if(db.update("UPDATE chapters SET title=?,position=? WHERE id=? AND course_id=?",c.title(),c.position(),id,c.courseId())==0)throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy chương.");audit(a.id(),"UPDATE_CHAPTER","CHAPTER",id,c.title());}
    @PostMapping("/lessons") Map<String,Long> lesson(@RequestHeader("Authorization") String t,@Valid @RequestBody Lesson l) {var a=auth.admin(t);long id=insert("lessons","chapter_id,title,content,material_url,position",l.chapterId(),l.title(),l.content(),l.materialUrl(),l.position());audit(a.id(),"CREATE_LESSON","LESSON",id,l.title());return Map.of("id",id);}
    @PutMapping("/lessons/{id}") void lesson(@PathVariable long id,@RequestHeader("Authorization") String t,@Valid @RequestBody Lesson l) {var a=auth.admin(t);if(db.update("UPDATE lessons SET title=?,content=?,material_url=?,position=? WHERE id=? AND chapter_id=?",l.title(),l.content(),l.materialUrl(),l.position(),id,l.chapterId())==0)throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy bài học.");audit(a.id(),"UPDATE_LESSON","LESSON",id,l.title());}
    @DeleteMapping("/lessons/{id}") void deleteLesson(@PathVariable long id,@RequestHeader("Authorization") String t) {var a=auth.admin(t);if(db.update("DELETE FROM lessons WHERE id=?",id)>0)audit(a.id(),"DELETE_LESSON","LESSON",id,"");}
    @PostMapping("/questions") Map<String,Long> question(@RequestHeader("Authorization") String t,@Valid @RequestBody Question q) {var a=auth.admin(t);long id=insert("questions","chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty",q.chapterId(),q.prompt(),q.options().get(0),q.options().get(1),q.options().get(2),q.options().get(3),q.correctOption(),q.explanation(),q.difficulty());audit(a.id(),"CREATE_QUESTION","QUESTION",id,q.prompt());return Map.of("id",id);}
    @PutMapping("/questions/{id}") void question(@PathVariable long id,@RequestHeader("Authorization") String t,@Valid @RequestBody Question q) {var a=auth.admin(t);if(db.update("UPDATE questions SET prompt=?,option_a=?,option_b=?,option_c=?,option_d=?,correct_option=?,explanation=?,difficulty=? WHERE id=? AND chapter_id=?",q.prompt(),q.options().get(0),q.options().get(1),q.options().get(2),q.options().get(3),q.correctOption(),q.explanation(),q.difficulty(),id,q.chapterId())==0)throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy câu hỏi.");audit(a.id(),"UPDATE_QUESTION","QUESTION",id,q.prompt());}
    @DeleteMapping("/questions/{id}") void deleteQuestion(@PathVariable long id,@RequestHeader("Authorization") String t) {var a=auth.admin(t);if(db.update("UPDATE questions SET active=FALSE WHERE id=? AND active=TRUE",id)>0)audit(a.id(),"DISABLE_QUESTION","QUESTION",id,"");}
    @GetMapping("/users") List<Map<String,Object>> users(@RequestHeader("Authorization") String t) {auth.admin(t);return db.queryForList("SELECT id,username,display_name,role,enabled FROM accounts ORDER BY id");}
    @PutMapping("/users/{id}") @Transactional void user(@PathVariable long id,@RequestHeader("Authorization") String t,@RequestBody UserState state) {
        var a=auth.admin(t);if(a.id()==id)throw new ResponseStatusException(CONFLICT,"Không thể tự khóa tài khoản quản trị.");
        int changed=db.update("UPDATE accounts SET enabled=? WHERE id=? AND role IN ('STUDENT','TEACHER')",state.enabled(),id);
        if(changed==0)throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy tài khoản có thể cập nhật.");
        if(!state.enabled())db.update("DELETE FROM sessions WHERE user_id=?",id);
        audit(a.id(),state.enabled()?"ENABLE_USER":"DISABLE_USER","ACCOUNT",id,"");
    }
    @PutMapping("/users/{id}/role") @Transactional void userRole(@PathVariable long id,@RequestHeader("Authorization") String t,@Valid @RequestBody UserRole role) {
        var a=auth.admin(t);
        if(a.id()==id)throw new ResponseStatusException(CONFLICT,"Không thể thay đổi vai trò tài khoản quản trị đang đăng nhập.");
        int changed=db.update("UPDATE accounts SET role=? WHERE id=? AND role<>'ADMIN'",role.role(),id);
        if(changed==0)throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy học viên.");
        db.update("DELETE FROM sessions WHERE user_id=?",id);
        audit(a.id(),"CHANGE_ROLE","ACCOUNT",id,role.role());
    }
    @GetMapping("/audit") List<Map<String,Object>> auditLog(@RequestHeader("Authorization") String t) {
        auth.admin(t);
        return db.queryForList("SELECT l.id,l.action,l.target_type,l.target_id,l.detail,l.created_at,a.display_name AS actor_name FROM admin_audit_log l JOIN accounts a ON a.id=l.actor_id ORDER BY l.id DESC LIMIT 200");
    }
    @GetMapping("/reports") Map<String,Object> reports(@RequestHeader("Authorization") String t) {
        auth.admin(t);
        return Map.of("students",db.queryForList("SELECT a.id,a.username,COUNT(q.id) AS attempts,AVG(100.0*q.score/q.total) AS average_score FROM accounts a LEFT JOIN quiz_attempts q ON q.user_id=a.id AND q.submitted_at IS NOT NULL WHERE a.role='STUDENT' GROUP BY a.id,a.username ORDER BY a.username"),"questions",db.queryForList("SELECT aa.question_id,aa.prompt,COUNT(*) AS attempts,SUM(CASE WHEN aa.selected_option=aa.correct_option THEN 0 ELSE 1 END) AS wrong FROM attempt_answers aa JOIN quiz_attempts q ON q.id=aa.attempt_id WHERE q.submitted_at IS NOT NULL GROUP BY aa.question_id,aa.prompt ORDER BY wrong DESC"));
    }
}
