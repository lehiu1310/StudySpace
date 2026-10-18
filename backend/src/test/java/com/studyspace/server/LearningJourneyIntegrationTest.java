package com.studyspace.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class LearningJourneyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    private String register(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("token").asText();
    }

    @Test
    void teacherCourseToStudentLessonQuizAndClassReport() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        register("journey_teacher" + suffix);
        db.update("UPDATE accounts SET role='TEACHER' WHERE username=?", "journey_teacher" + suffix);
        String teacher = registerAgain("journey_teacher" + suffix);
        String student = register("journey_student" + suffix);

        JsonNode course = json.readTree(mvc.perform(post("/api/teacher/content/courses").header("Authorization", teacher)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Journey " + suffix + "\",\"description\":\"End-to-end lesson\",\"published\":false}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long courseId = course.get("id").asLong();
        JsonNode chapter = json.readTree(mvc.perform(post("/api/teacher/content/chapters").header("Authorization", teacher)
            .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + ",\"title\":\"Core\",\"position\":1}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long chapterId = chapter.get("id").asLong();
        JsonNode lesson = json.readTree(mvc.perform(post("/api/teacher/content/lessons").header("Authorization", teacher)
            .contentType(MediaType.APPLICATION_JSON).content("{\"chapterId\":" + chapterId + ",\"title\":\"First lesson\",\"content\":\"Read and practice\",\"materialUrl\":\"\",\"position\":1}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long lessonId = lesson.get("id").asLong();
        mvc.perform(post("/api/teacher/content/questions").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
            .content("{\"chapterId\":" + chapterId + ",\"prompt\":\"Which answer is correct?\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctOption\":2,\"explanation\":\"C is correct\",\"difficulty\":1}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/teacher/content/courses/" + courseId).header("Authorization", teacher)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Journey " + suffix + "\",\"description\":\"End-to-end lesson\",\"published\":true}"))
            .andExpect(status().isOk());
        JsonNode studyClass = json.readTree(mvc.perform(post("/api/teacher/classes").header("Authorization", teacher)
            .contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":" + courseId + ",\"name\":\"Journey class\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/classes/join").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + studyClass.get("joinCode").asText() + "\"}"))
            .andExpect(status().isOk());

        JsonNode beforeStart = json.readTree(mvc.perform(get("/api/courses").header("Authorization", student))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode newCourse = null;
        for (JsonNode item : beforeStart) if (item.path("id").asLong() == courseId) newCourse = item;
        assertNotNull(newCourse);
        assertTrue(newCourse.path("started_at").isNull());
        mvc.perform(put("/api/courses/" + courseId + "/start").header("Authorization", teacher))
            .andExpect(status().isForbidden());
        JsonNode startResult = json.readTree(mvc.perform(put("/api/courses/" + courseId + "/start").header("Authorization", student))
            .andExpect(status().isOk()).andExpect(jsonPath("$.startedAt").isNumber())
            .andReturn().getResponse().getContentAsString());
        long startedAt = startResult.path("startedAt").asLong();
        mvc.perform(put("/api/courses/" + courseId + "/start").header("Authorization", student)).andExpect(status().isOk());
        assertEquals(startedAt, db.queryForObject("SELECT started_at FROM course_learning WHERE user_id=(SELECT id FROM accounts WHERE username=?) AND course_id=?", Long.class, "journey_student" + suffix, courseId));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM course_learning WHERE user_id=(SELECT id FROM accounts WHERE username=?) AND course_id=?", Integer.class, "journey_student" + suffix, courseId));

        JsonNode visibleCourse = json.readTree(mvc.perform(get("/api/courses/" + courseId).header("Authorization", student))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("First lesson", visibleCourse.path("chapters").get(0).path("lessons").get(0).path("title").asText());
        assertFalse(visibleCourse.path("chapters").get(0).path("lessons").get(0).path("locked").asBoolean());
        mvc.perform(put("/api/lessons/" + lessonId + "/complete").header("Authorization", student)).andExpect(status().isOk());

        JsonNode attempt = json.readTree(mvc.perform(post("/api/chapters/" + chapterId + "/attempts").header("Authorization", student))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long questionId = attempt.path("questions").get(0).path("question_id").asLong();
        assertFalse(attempt.path("questions").get(0).has("correct_option"));
        int correct = db.queryForObject("SELECT correct_option FROM questions WHERE id=?", Integer.class, questionId);
        mvc.perform(put("/api/attempts/" + attempt.get("id").asLong() + "/answers").header("Authorization", student)
            .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"" + questionId + "\":" + correct + "}}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/attempts/" + attempt.get("id").asLong() + "/submit").header("Authorization", student))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1));
        mvc.perform(get("/api/teacher/classes/" + studyClass.get("id").asLong() + "/report").header("Authorization", teacher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.summary.student_count").value(1))
            .andExpect(jsonPath("$.summary.lesson_completed").value(1)).andExpect(jsonPath("$.summary.quiz_attempts").value(1));
    }

    private String registerAgain(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("token").asText();
    }
}
