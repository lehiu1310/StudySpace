package com.studyspace.server;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ClassIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    String register(String username) throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\""+username+"\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(response).get("token").asText();
    }

    String promoteToTeacher(String username,String studentToken) throws Exception {
        long userId=db.queryForObject("SELECT id FROM accounts WHERE username=?",Long.class,username);
        var admin=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"Test-only-password-123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminToken="Bearer "+json.readTree(admin).get("token").asText();
        mvc.perform(put("/api/admin/users/"+userId+"/role").header("Authorization",adminToken)
            .contentType("application/json").content("{\"role\":\"TEACHER\"}"))
            .andExpect(status().isOk());
        return studentToken;
    }

    @Test void emailStyleTeacherAccountCanRegisterAndBePromoted() throws Exception {
        String username="teacher-test@example.invalid";
        String response=mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\""+username+"\",\"password\":\"Test-Only-Pass-123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String studentToken="Bearer "+json.readTree(response).get("token").asText();
        promoteToTeacher(username,studentToken);
        var login=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\""+username+"\",\"password\":\"Test-Only-Pass-123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+json.readTree(login).get("token").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"));
    }

    @Test void teacherCreatesClassStudentJoinsAndOnlyOwnerSeesReport() throws Exception {
        String teacherToken=register("class_teacher");
        promoteToTeacher("class_teacher",teacherToken);
        teacherToken="Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"class_teacher\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        String courseTitle="Class test "+System.nanoTime();
        db.update("INSERT INTO courses(title,description,published) VALUES(?,?,TRUE)",courseTitle,"Isolated class report fixture");
        long course=db.queryForObject("SELECT id FROM courses WHERE title=?",Long.class,courseTitle);
        db.update("INSERT INTO chapters(course_id,title,position) VALUES(?,?,1)",course,"Class chapter");
        long chapterId=db.queryForObject("SELECT id FROM chapters WHERE course_id=?",Long.class,course);
        db.update("INSERT INTO lessons(chapter_id,title,content,position) VALUES(?,?,?,1)",chapterId,"Class lesson","Lesson content");
        db.update("INSERT INTO questions(chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty) VALUES(?,?,?,?,?,?,?,?,1)",chapterId,"Class quiz question","A","B","C","D",0,"Correct answer A");
        var created=json.readTree(mvc.perform(post("/api/teacher/classes").header("Authorization",teacherToken)
            .contentType("application/json").content("{\"courseId\":"+course+",\"name\":\"Lớp ôn tập\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long classId=created.get("id").asLong();
        String code=created.get("joinCode").asText();

        String studentToken=register("class_student");
        String joinBody="{\"code\":\""+code+"\"}";
        mvc.perform(post("/api/classes/join").header("Authorization",studentToken).contentType("application/json").content(joinBody))
            .andExpect(status().isOk());
        mvc.perform(post("/api/classes/join").header("Authorization",studentToken).contentType("application/json").content(joinBody))
            .andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE class_id=?",Integer.class,classId));
        long studentId=db.queryForObject("SELECT id FROM accounts WHERE username='class_student'",Long.class);
        var lessonIds=db.queryForList("SELECT id FROM lessons WHERE chapter_id=?",Long.class,chapterId);
        int courseLessonCount=db.queryForObject("SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=?",Integer.class,course);
        for(long lessonId:lessonIds)db.update("INSERT INTO lesson_progress(user_id,lesson_id,completed_at) VALUES(?,?,?)",studentId,lessonId,System.currentTimeMillis());
        var question=db.queryForMap("SELECT * FROM questions WHERE chapter_id=? ORDER BY id LIMIT 1",chapterId);
        long now=System.currentTimeMillis();
        db.update("INSERT INTO quiz_attempts(user_id,chapter_id,started_at,deadline,submitted_at,score,total) VALUES(?,?,?,?,?,?,?)",studentId,chapterId,now-1000,now-500,now,0,1);
        long attemptId=db.queryForObject("SELECT MAX(id) FROM quiz_attempts WHERE user_id=?",Long.class,studentId);
        db.update("INSERT INTO attempt_answers(attempt_id,question_id,position,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,selected_option) VALUES(?,?,?,?,?,?,?,?,?,?,?)",attemptId,question.get("id"),0,question.get("prompt"),question.get("option_a"),question.get("option_b"),question.get("option_c"),question.get("option_d"),question.get("correct_option"),question.get("explanation"),(((Number)question.get("correct_option")).intValue()+1)%4);
        mvc.perform(get("/api/classes/mine").header("Authorization",studentToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].course_title").exists());
        mvc.perform(get("/api/teacher/classes/"+classId+"/report").header("Authorization",teacherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.student_count").value(1))
            .andExpect(jsonPath("$.summary.lesson_total").value(courseLessonCount))
            .andExpect(jsonPath("$.summary.lesson_completed").value(lessonIds.size()))
            .andExpect(jsonPath("$.chapters[0].quiz_attempts").value(1))
            .andExpect(jsonPath("$.questions[0].wrong").value(1))
            .andExpect(jsonPath("$.students[0].lesson_completed").value(lessonIds.size()))
            .andExpect(jsonPath("$.heatmap[0].student_id").value(studentId));
        mvc.perform(post("/api/teacher/classes/"+classId+"/announcements").header("Authorization",teacherToken)
                .contentType("application/json").content("{\"body\":\"Ôn lại chương trước buổi học sau nhé.\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/classes/announcements").header("Authorization",studentToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].body").value("Ôn lại chương trước buổi học sau nhé."))
            .andExpect(jsonPath("$[0].teacher_name").exists());
        mvc.perform(post("/api/teacher/classes/"+classId+"/announcements").header("Authorization",studentToken)
                .contentType("application/json").content("{\"body\":\"Không được gửi\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/classes/"+classId+"/report").header("Authorization",studentToken))
            .andExpect(status().isForbidden());

        String otherTeacher=register("class_teacher2");
        promoteToTeacher("class_teacher2",otherTeacher);
        otherTeacher="Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"class_teacher2\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/teacher/classes/"+classId+"/report").header("Authorization",otherTeacher))
            .andExpect(status().isNotFound());
    }
}
