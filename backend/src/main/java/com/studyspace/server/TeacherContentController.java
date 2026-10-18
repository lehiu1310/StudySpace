package com.studyspace.server;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/teacher/content")
class TeacherContentController {
    private final JdbcTemplate db;
    private final AuthService auth;
    private final StudyMaterialStorage materials;
    private final CoursePublicationPolicy publicationPolicy;
    TeacherContentController(JdbcTemplate db,AuthService auth,StudyMaterialStorage materials,CoursePublicationPolicy publicationPolicy) {
        this.db=db;this.auth=auth;this.materials=materials;this.publicationPolicy=publicationPolicy;
    }

    @GetMapping Map<String,Object> content(@RequestHeader("Authorization") String token) {
        var teacher=auth.teacher(token);
        return Map.of(
            "courses",db.queryForList("SELECT * FROM courses WHERE created_by=? ORDER BY id",teacher.id()),
            "chapters",db.queryForList("SELECT ch.* FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE c.created_by=? ORDER BY c.id,ch.position,ch.id",teacher.id()),
            "lessons",db.queryForList("SELECT l.* FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.created_by=? ORDER BY c.id,ch.position,ch.id,l.position,l.id",teacher.id()),
            "questions",db.queryForList("SELECT q.* FROM questions q JOIN chapters ch ON ch.id=q.chapter_id JOIN courses c ON c.id=ch.course_id WHERE c.created_by=? ORDER BY q.id",teacher.id()));
    }

    @PostMapping("/courses") Map<String,Long> createCourse(@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Course body) {
        var teacher=auth.teacher(token);
        if(body.published())throw new ResponseStatusException(BAD_REQUEST,"Hãy lưu khóa học ở dạng bản nháp, thêm chương và bài học có nội dung thật rồi mới xuất bản.");
        return Map.of("id",insert("courses","title,description,published,created_by",body.title(),body.description(),body.published(),teacher.id()));
    }
    @PutMapping("/courses/{id}") void updateCourse(@PathVariable long id,@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Course body) {
        var teacher=auth.teacher(token);
        if(body.published()){
            ownCourse(id,teacher.id());
            publicationPolicy.validate(id,body.title(),body.description());
        }
        if(db.update("UPDATE courses SET title=?,description=?,published=? WHERE id=? AND created_by=?",body.title(),body.description(),body.published(),id,teacher.id())==0)notFound();
    }
    @PostMapping(value="/materials",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String,Object> uploadMaterial(@RequestHeader("Authorization") String token,@RequestParam("chapterId") long chapterId,@RequestParam("file") MultipartFile file) {
        var teacher=auth.teacher(token);ownChapter(chapterId,teacher.id());
        try {
            var stored=materials.store(file);
            return Map.of("url","/materials/"+stored.filename(),"originalName",stored.originalName(),"size",stored.size());
        } catch(java.io.IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,"Không thể lưu tài liệu.");
        }
    }

    @PostMapping("/chapters") Map<String,Long> createChapter(@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Chapter body) {
        var teacher=auth.teacher(token); ownCourse(body.courseId(),teacher.id());
        return Map.of("id",insert("chapters","course_id,title,position",body.courseId(),body.title(),body.position()));
    }
    @PutMapping("/chapters/{id}") void updateChapter(@PathVariable long id,@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Chapter body) {
        var teacher=auth.teacher(token); ownCourse(body.courseId(),teacher.id());
        if(db.update("UPDATE chapters SET course_id=?,title=?,position=? WHERE id=? AND EXISTS (SELECT 1 FROM courses c WHERE c.id=chapters.course_id AND c.created_by=?)",body.courseId(),body.title(),body.position(),id,teacher.id())==0)notFound();
    }

    @PostMapping("/lessons") Map<String,Long> createLesson(@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Lesson body) {
        var teacher=auth.teacher(token); ownChapter(body.chapterId(),teacher.id());
        return Map.of("id",insert("lessons","chapter_id,title,content,material_url,position",body.chapterId(),body.title(),body.content(),body.materialUrl(),body.position()));
    }
    @PutMapping("/lessons/{id}") void updateLesson(@PathVariable long id,@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Lesson body) {
        var teacher=auth.teacher(token); ownChapter(body.chapterId(),teacher.id());
        if(db.update("UPDATE lessons SET chapter_id=?,title=?,content=?,material_url=?,position=? WHERE id=? AND EXISTS (SELECT 1 FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE ch.id=lessons.chapter_id AND c.created_by=?)",body.chapterId(),body.title(),body.content(),body.materialUrl(),body.position(),id,teacher.id())==0)notFound();
    }

    @PostMapping("/questions") Map<String,Long> createQuestion(@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Question body) {
        var teacher=auth.teacher(token); ownChapter(body.chapterId(),teacher.id());
        return Map.of("id",insert("questions","chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty",body.chapterId(),body.prompt(),body.options().get(0),body.options().get(1),body.options().get(2),body.options().get(3),body.correctOption(),body.explanation(),body.difficulty()));
    }
    @PutMapping("/questions/{id}") void updateQuestion(@PathVariable long id,@RequestHeader("Authorization") String token,@Valid @RequestBody AdminController.Question body) {
        var teacher=auth.teacher(token); ownChapter(body.chapterId(),teacher.id());
        if(db.update("UPDATE questions SET chapter_id=?,prompt=?,option_a=?,option_b=?,option_c=?,option_d=?,correct_option=?,explanation=?,difficulty=? WHERE id=? AND EXISTS (SELECT 1 FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE ch.id=questions.chapter_id AND c.created_by=?)",body.chapterId(),body.prompt(),body.options().get(0),body.options().get(1),body.options().get(2),body.options().get(3),body.correctOption(),body.explanation(),body.difficulty(),id,teacher.id())==0)notFound();
    }

    private long insert(String table,String columns,Object... values) {
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(con->{var statement=con.prepareStatement("INSERT INTO "+table+"("+columns+") VALUES("+String.join(",",Collections.nCopies(values.length,"?"))+")",new String[]{"id"});for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private void ownCourse(long id,long teacherId) {
        if(db.queryForObject("SELECT COUNT(*) FROM courses WHERE id=? AND created_by=?",Integer.class,id,teacherId)==0)notFound();
    }
    private void ownChapter(long id,long teacherId) {
        if(db.queryForObject("SELECT COUNT(*) FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE ch.id=? AND c.created_by=?",Integer.class,id,teacherId)==0)notFound();
    }
    private void notFound() { throw new ResponseStatusException(NOT_FOUND,"Không tìm thấy nội dung bạn có quyền quản lý."); }
}
