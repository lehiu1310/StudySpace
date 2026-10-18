package com.studyspace.server;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class LearningIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired LearningController learning;
    @Autowired Bootstrap bootstrap;
    String register(String user) throws Exception {
        return "Bearer "+json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\""+user+"\",\"password\":\"Password123!\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    @Test void demoCatalogHasMultipleCoursesAndEnoughPracticeContent() {
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM courses WHERE published=TRUE",Integer.class)>=4);
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM lessons",Integer.class)>=20);
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE",Integer.class)>=30);
        var deficientCourses = db.queryForList("SELECT c.title,(SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=c.id) AS lesson_count FROM courses c WHERE c.title IN ('Cơ sở dữ liệu','Lập trình Kotlin','Thuật toán và cấu trúc dữ liệu','Mạng máy tính và Web căn bản') AND (SELECT COUNT(*) FROM lessons l JOIN chapters ch ON ch.id=l.chapter_id WHERE ch.course_id=c.id)<5");
        assertTrue(deficientCourses.isEmpty(), deficientCourses.toString());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND (explanation IS NULL OR TRIM(explanation)='')", Integer.class));
        for (int difficulty = 1; difficulty <= 3; difficulty++)
            assertTrue(db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND difficulty=?", Integer.class, difficulty) > 0);

        int lessonCount = db.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class);
        int questionCount = db.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
        ReflectionTestUtils.invokeMethod(bootstrap, "ensureDemoContentCoverage");
        assertEquals(lessonCount, db.queryForObject("SELECT COUNT(*) FROM lessons", Integer.class));
        assertEquals(questionCount, db.queryForObject("SELECT COUNT(*) FROM questions", Integer.class));
    }
    @Test void legacyPasswordHashesUpgradeToBcryptAfterSuccessfulLogin() throws Exception {
        String password = "LegacyPassword123!";
        String sha256 = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((password + "StudySpaceSecureSalt123!").getBytes(StandardCharsets.UTF_8)));
        for (var entry : java.util.Map.of("legacy_sha", sha256, "legacy_plain", password).entrySet()) {
            db.update("INSERT INTO accounts(username,display_name,password_hash,role) VALUES(?,?,?,'STUDENT')",
                entry.getKey(), entry.getKey(), entry.getValue());
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content(json.createObjectNode().put("username", entry.getKey()).put("password", password).toString()))
                .andExpect(status().isOk());
            String upgraded = db.queryForObject("SELECT password_hash FROM accounts WHERE username=?", String.class, entry.getKey());
            assertTrue(upgraded.startsWith("$2"));
            assertTrue(new BCryptPasswordEncoder(12).matches(password, upgraded));
        }
    }
    @Test void completeLearningFlowProtectsAnswersAndOwnership() throws Exception {
        String token=register("student_flow");
        mvc.perform(get("/api/admin/users").header("Authorization",token)).andExpect(status().isForbidden());
        long chapter=db.queryForObject("SELECT MIN(id) FROM chapters",Long.class);
        JsonNode attempt=json.readTree(mvc.perform(post("/api/chapters/"+chapter+"/attempts").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long id=attempt.get("id").asLong();
        assertFalse(attempt.get("questions").get(0).has("correct_option"));
        mvc.perform(get("/api/attempts/"+id).header("Authorization",register("student_other"))).andExpect(status().isNotFound());
        var answers=json.createObjectNode();
        for(JsonNode q:attempt.get("questions")) answers.put(q.get("question_id").asText(),db.queryForObject("SELECT correct_option FROM questions WHERE id=?",Integer.class,q.get("question_id").asLong()));
        mvc.perform(put("/api/attempts/"+id+"/answers").header("Authorization",token).contentType("application/json").content(json.createObjectNode().set("answers",answers).toString())).andExpect(status().isOk());
        mvc.perform(post("/api/attempts/"+id+"/submit").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(attempt.get("total").asInt())).andExpect(jsonPath("$.questions[0].explanation").exists());
        mvc.perform(post("/api/attempts/"+id+"/submit").header("Authorization",token)).andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE id=? AND submitted_at IS NOT NULL",Integer.class,id));
        long lesson=db.queryForObject("SELECT MIN(id) FROM lessons",Long.class);
        for(int i=0;i<2;i++)mvc.perform(put("/api/lessons/"+lesson+"/complete").header("Authorization",token)).andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM lesson_progress WHERE lesson_id=?",Integer.class,lesson));
        mvc.perform(get("/api/progress").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.lessonActivity[0].completed_at").isNumber());
    }
    @Test void generatedReviewQuizUsesNormalAttemptScoringAndHistory() throws Exception {
        String username = "student_ai_review_" + System.nanoTime();
        String token = register(username);
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        long chapter = db.queryForObject("SELECT MIN(id) FROM chapters", Long.class);
        var questions = java.util.stream.IntStream.range(0, 5)
            .mapToObj(i -> new LearningController.AiGeneratedQuestion(
                "Generated question number " + i + "?", java.util.List.of("A", "B", "C", "D"), 2,
                "The explanation for generated question " + i + "."))
            .toList();

        JsonNode attempt = json.valueToTree(learning.createAiReviewAttempt(token, chapter, questions));
        assertEquals(5, attempt.get("total").asInt());
        assertEquals(5, attempt.get("questions").size());
        assertTrue(attempt.get("questions").get(0).get("question_id").asLong() > 0);
        assertFalse(attempt.get("questions").get(0).has("correct_option"));
        var answers = json.createObjectNode();
        for (JsonNode question : attempt.get("questions")) answers.put(question.get("question_id").asText(), 2);

        mvc.perform(put("/api/attempts/" + attempt.get("id").asLong() + "/answers")
                .header("Authorization", token).contentType("application/json")
                .content(json.createObjectNode().set("answers", answers).toString()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/attempts/" + attempt.get("id").asLong() + "/submit").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(5));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE id=? AND user_id=? AND submitted_at IS NOT NULL", Integer.class, attempt.get("id").asLong(), userId));
    }
    @Test void deadlineRejectsLateAnswersAndLogoutRevokesToken() throws Exception {
        String token=register("student_timeout");
        long chapter=db.queryForObject("SELECT MIN(id) FROM chapters",Long.class);
        JsonNode a=json.readTree(mvc.perform(post("/api/chapters/"+chapter+"/attempts").header("Authorization",token)).andReturn().getResponse().getContentAsString());
        long id=a.get("id").asLong(); db.update("UPDATE quiz_attempts SET deadline=0 WHERE id=?",id);
        mvc.perform(put("/api/attempts/"+id+"/answers").header("Authorization",token).contentType("application/json").content("{\"answers\":{}}")).andExpect(status().isConflict());
        mvc.perform(post("/api/attempts/"+id+"/submit").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));
        mvc.perform(post("/api/auth/logout").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/courses").header("Authorization",token)).andExpect(status().isUnauthorized());
    }
    @Test void editingQuestionDoesNotChangeExistingAttemptAndNewAccountHasNoScores() throws Exception {
        String token=register("student_snapshot");
        mvc.perform(get("/api/progress").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.history").isEmpty()).andExpect(jsonPath("$.mastery[0].percentage").isEmpty());
        long chapter=db.queryForObject("SELECT MIN(id) FROM chapters",Long.class);
        JsonNode a=json.readTree(mvc.perform(post("/api/chapters/"+chapter+"/attempts").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode q=a.get("questions").get(0);
        long question=q.get("question_id").asLong();
        String original=q.get("prompt").asText();
        try {
            db.update("UPDATE questions SET prompt='Updated after start' WHERE id=?",question);
            mvc.perform(get("/api/attempts/"+a.get("id").asLong()).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].prompt").value(original));
        } finally { db.update("UPDATE questions SET prompt=? WHERE id=?",original,question); }
    }
    @Test void discussionsShareRealMessagesAndRemovePresenceOnLeave() throws Exception {
        String first=register("chat_first");
        String second=register("chat_second");
        String teacher=register("chat_teacher");
        register("chat_outsider");
        long firstId=db.queryForObject("SELECT id FROM accounts WHERE username='chat_first'",Long.class);
        long secondId=db.queryForObject("SELECT id FROM accounts WHERE username='chat_second'",Long.class);
        long teacherId=db.queryForObject("SELECT id FROM accounts WHERE username='chat_teacher'",Long.class);
        db.update("UPDATE accounts SET display_name='Minh Anh' WHERE id=?",firstId);
        db.update("UPDATE accounts SET role='TEACHER' WHERE id=?",teacherId);
        long course=db.queryForObject("SELECT MIN(id) FROM courses",Long.class);
        db.update("INSERT INTO study_classes(course_id,teacher_id,name,join_code,created_at) VALUES(?,?,?,?,?)",course,teacherId,"Discussion class","CHAT1234",System.currentTimeMillis());
        long classId=db.queryForObject("SELECT id FROM study_classes WHERE join_code='CHAT1234'",Long.class);
        db.update("INSERT INTO class_enrollments(class_id,student_id,enrolled_at) VALUES(?,?,?)",classId,firstId,System.currentTimeMillis());
        db.update("INSERT INTO class_enrollments(class_id,student_id,enrolled_at) VALUES(?,?,?)",classId,secondId,System.currentTimeMillis());
        String route="/api/courses/"+course+"/discussion";
        mvc.perform(get(route)).andExpect(status().isBadRequest());
        mvc.perform(post(route).header("Authorization",first).contentType("application/json").content("{\"body\":\"How do foreign keys work?\"}")).andExpect(status().isOk());
        mvc.perform(get(route).header("Authorization",first)).andExpect(status().isOk()).andExpect(jsonPath("$.online.length()").value(1));
        mvc.perform(get(route).header("Authorization",second)).andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].body").value("How do foreign keys work?")).andExpect(jsonPath("$.messages[0].username").value("chat_first")).andExpect(jsonPath("$.messages[0].display_name").value("Minh Anh")).andExpect(jsonPath("$.online.length()").value(2));
        mvc.perform(get(route).header("Authorization",teacher)).andExpect(status().isOk());
        String outsider="Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"chat_outsider\",\"password\":\"Password123!\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get(route).header("Authorization",outsider)).andExpect(status().isForbidden());
        mvc.perform(delete(route).header("Authorization",first)).andExpect(status().isOk());
        mvc.perform(get(route).header("Authorization",second)).andExpect(status().isOk()).andExpect(jsonPath("$.online.length()").value(2));
    }

    @Test void nextQuizAdaptsToRecentLowMastery() throws Exception {
        String token=register("student_adaptive");
        String title="Adaptive "+System.nanoTime();
        db.update("INSERT INTO courses(title,description,published) VALUES(?,?,TRUE)",title,"Quiz adaptation test");
        long course=db.queryForObject("SELECT id FROM courses WHERE title=?",Long.class,title);
        db.update("INSERT INTO chapters(course_id,title,position) VALUES(?,?,0)",course,"Adaptive chapter");
        long chapter=db.queryForObject("SELECT id FROM chapters WHERE course_id=?",Long.class,course);
        for(int i=0;i<20;i++) {
            int difficulty=i<12?1:i<16?2:3;
            db.update("INSERT INTO questions(chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty) VALUES(?,?,?,?,?,?,?,?,?)",
                chapter,"Question "+i,"A","B","C","D",0,"Answer A",difficulty);
        }

        JsonNode first=json.readTree(mvc.perform(post("/api/chapters/"+chapter+"/attempts").header("Authorization",token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var answers=json.createObjectNode();
        for(JsonNode question:first.get("questions")) answers.put(question.get("question_id").asText(),1);
        mvc.perform(put("/api/attempts/"+first.get("id").asLong()+"/answers").header("Authorization",token)
            .contentType("application/json").content(json.createObjectNode().set("answers",answers).toString())).andExpect(status().isOk());
        mvc.perform(post("/api/attempts/"+first.get("id").asLong()+"/submit").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0));

        JsonNode next=json.readTree(mvc.perform(post("/api/chapters/"+chapter+"/attempts").header("Authorization",token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var selectedDifficulties=db.queryForList("SELECT DISTINCT q.difficulty FROM attempt_answers aa JOIN questions q ON q.id=aa.question_id WHERE aa.attempt_id=?",Integer.class,next.get("id").asLong());
        assertEquals(java.util.List.of(1),selectedDifficulties);
    }
}
