package com.studyspace.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="studyspace.upload-dir=backend/build/test-uploads") @AutoConfigureMockMvc
class TeacherContentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    String register(String username) throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\""+username+"\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(response).get("token").asText();
    }
    String promote(String username) throws Exception {
        long id=db.queryForObject("SELECT id FROM accounts WHERE username=?",Long.class,username);
        var response=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"Test-only-password-123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String admin="Bearer "+json.readTree(response).get("token").asText();
        mvc.perform(put("/api/admin/users/"+id+"/role").header("Authorization",admin).contentType("application/json").content("{\"role\":\"TEACHER\"}"))
            .andExpect(status().isOk());
        var login=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\""+username+"\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(login).get("token").asText();
    }

    @Test void teacherCanManageOwnCourseContentButCannotTouchAnotherTeachersContent() throws Exception {
        register("author_one");
        String owner=promote("author_one");
        register("author_two");
        String other=promote("author_two");
        var ticket=json.readTree(mvc.perform(post("/api/auth/portal-ticket").header("Authorization",owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String ticketValue=ticket.get("ticket").asText();
        var exchanged=json.readTree(mvc.perform(post("/api/auth/portal-ticket/exchange").contentType("application/json")
            .content("{\"ticket\":\""+ticketValue+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.account.role").value("TEACHER"))
            .andReturn().getResponse().getContentAsString());
        owner="Bearer "+exchanged.get("token").asText();
        mvc.perform(post("/api/auth/portal-ticket/exchange").contentType("application/json")
            .content("{\"ticket\":\""+ticketValue+"\"}"))
            .andExpect(status().isUnauthorized());
        var course=json.readTree(mvc.perform(post("/api/teacher/content/courses").header("Authorization",owner)
            .contentType("application/json").content("{\"title\":\"Owned course\",\"description\":\"Course description\",\"published\":false}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long courseId=course.get("id").asLong();
        mvc.perform(put("/api/teacher/content/courses/"+courseId).header("Authorization",owner).contentType("application/json")
            .content("{\"title\":\"Owned course updated\",\"description\":\"Updated description\",\"published\":false}"))
            .andExpect(status().isOk());
        var chapter=json.readTree(mvc.perform(post("/api/teacher/content/chapters").header("Authorization",owner)
            .contentType("application/json").content("{\"courseId\":"+courseId+",\"title\":\"Chapter 1\",\"position\":1}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long chapterId=chapter.get("id").asLong();
        mvc.perform(put("/api/teacher/content/chapters/"+chapterId).header("Authorization",owner).contentType("application/json")
            .content("{\"courseId\":"+courseId+",\"title\":\"Chapter 1 reordered\",\"position\":2}"))
            .andExpect(status().isOk());
        var uploaded=json.readTree(mvc.perform(multipart("/api/teacher/content/materials")
            .file(new MockMultipartFile("file","slides.pdf","application/pdf","%PDF-1.7\nStudySpace sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            .param("chapterId",String.valueOf(chapterId)).header("Authorization",owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String materialUrl=uploaded.get("url").asText();
        mvc.perform(get(materialUrl)).andExpect(status().isOk()).andExpect(content().bytes("%PDF-1.7\nStudySpace sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        mvc.perform(multipart("/api/teacher/content/materials")
            .file(new MockMultipartFile("file","fake.pdf","application/pdf","MZ not a PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            .param("chapterId",String.valueOf(chapterId)).header("Authorization",owner))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/teacher/content/materials")
            .file(new MockMultipartFile("file","foreign.pdf","application/pdf","%PDF-1.7\nForeign".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            .param("chapterId",String.valueOf(chapterId)).header("Authorization",other))
            .andExpect(status().isNotFound());
        var lesson=json.readTree(mvc.perform(post("/api/teacher/content/lessons").header("Authorization",owner).contentType("application/json")
            .content("{\"chapterId\":"+chapterId+",\"title\":\"Lesson 1\",\"content\":\"Lesson body\",\"materialUrl\":\""+materialUrl+"\",\"position\":1}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long lessonId=lesson.get("id").asLong();
        mvc.perform(put("/api/teacher/content/lessons/"+lessonId).header("Authorization",owner).contentType("application/json")
            .content("{\"chapterId\":"+chapterId+",\"title\":\"Lesson 1 reordered\",\"content\":\"Updated lesson body\",\"materialUrl\":\""+materialUrl+"\",\"position\":3}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/teacher/content/questions").header("Authorization",owner).contentType("application/json")
            .content("{\"chapterId\":"+chapterId+",\"prompt\":\"Question\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctOption\":0,\"explanation\":\"Because A\",\"difficulty\":1}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/teacher/content/courses/"+courseId).header("Authorization",owner).contentType("application/json")
            .content("{\"title\":\"Owned course updated\",\"description\":\"Updated description\",\"published\":true}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/teacher/content").header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.courses[0].title").value("Owned course updated"))
            .andExpect(jsonPath("$.chapters[0].position").value(2))
            .andExpect(jsonPath("$.lessons[0].position").value(3))
            .andExpect(jsonPath("$.lessons[0].material_url").value(materialUrl));
        mvc.perform(put("/api/teacher/content/courses/"+courseId).header("Authorization",other).contentType("application/json")
            .content("{\"title\":\"Takeover\",\"description\":\"No\",\"published\":true}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/teacher/content/lessons").header("Authorization",other).contentType("application/json")
            .content("{\"chapterId\":"+chapterId+",\"title\":\"Unauthorized\",\"content\":\"No\",\"materialUrl\":\"\",\"position\":2}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/teacher/content").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.courses.length()").value(0));
    }

    @Test void publicationRejectsEmptyAndPlaceholderContentUntilCourseIsComplete() throws Exception {
        String username = "publish_gate_" + System.nanoTime();
        register(username);
        String token = promote(username);
        var course = json.readTree(mvc.perform(post("/api/teacher/content/courses").header("Authorization", token)
                .contentType("application/json").content("{\"title\":\"Security fundamentals\",\"description\":\"A complete course description\",\"published\":false}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long courseId = course.path("id").asLong();
        mvc.perform(put("/api/teacher/content/courses/" + courseId).header("Authorization", token).contentType("application/json")
                .content("{\"title\":\"Security fundamentals\",\"description\":\"A complete course description\",\"published\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("ít nhất một chương")));

        var chapter = json.readTree(mvc.perform(post("/api/teacher/content/chapters").header("Authorization", token).contentType("application/json")
                .content("{\"courseId\":" + courseId + ",\"title\":\"Chương tiếp theo trong lộ trình\",\"position\":1}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long chapterId = chapter.path("id").asLong();
        mvc.perform(post("/api/teacher/content/lessons").header("Authorization", token).contentType("application/json")
                .content("{\"chapterId\":" + chapterId + ",\"title\":\"Bài học: Chương tiếp theo trong lộ trình\",\"content\":\"Lorem ipsum placeholder\",\"materialUrl\":\"\",\"position\":1}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/teacher/content/courses/" + courseId).header("Authorization", token).contentType("application/json")
                .content("{\"title\":\"Security fundamentals\",\"description\":\"A complete course description\",\"published\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("tiêu đề mẫu")));

        mvc.perform(put("/api/teacher/content/chapters/" + chapterId).header("Authorization", token).contentType("application/json")
                .content("{\"courseId\":" + courseId + ",\"title\":\"Threat modeling\",\"position\":1}"))
            .andExpect(status().isOk());
        var lesson = json.readTree(mvc.perform(get("/api/teacher/content").header("Authorization", token)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("lessons").get(0);
        long lessonId = lesson.path("id").asLong();
        mvc.perform(put("/api/teacher/content/lessons/" + lessonId).header("Authorization", token).contentType("application/json")
                .content("{\"chapterId\":" + chapterId + ",\"title\":\"Threat modeling\",\"content\":\"Lorem ipsum placeholder\",\"materialUrl\":\"\",\"position\":1}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/teacher/content/courses/" + courseId).header("Authorization", token).contentType("application/json")
                .content("{\"title\":\"Security fundamentals\",\"description\":\"A complete course description\",\"published\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("chưa có nội dung thật")));
        mvc.perform(put("/api/teacher/content/lessons/" + lessonId).header("Authorization", token).contentType("application/json")
                .content("{\"chapterId\":" + chapterId + ",\"title\":\"Threat modeling\",\"content\":\"A real lesson with enough detail about assets, attackers, trust boundaries, confidentiality and integrity.\",\"materialUrl\":\"\",\"position\":1}"))
            .andExpect(status().isOk());
        var emptyChapter = json.readTree(mvc.perform(post("/api/teacher/content/chapters").header("Authorization", token).contentType("application/json")
                .content("{\"courseId\":" + courseId + ",\"title\":\"Secure implementation\",\"position\":2}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/teacher/content/courses/" + courseId).header("Authorization", token).contentType("application/json")
                .content("{\"title\":\"Security fundamentals\",\"description\":\"A complete course description\",\"published\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("chưa có bài học")));
        mvc.perform(post("/api/teacher/content/lessons").header("Authorization", token).contentType("application/json")
                .content("{\"chapterId\":" + emptyChapter.path("id").asLong() + ",\"title\":\"Input validation\",\"content\":\"Validate input size and reject values that exceed explicit resource limits before expensive processing begins.\",\"materialUrl\":\"\",\"position\":1}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/teacher/content/courses/" + courseId).header("Authorization", token).contentType("application/json")
                .content("{\"title\":\"Security fundamentals\",\"description\":\"A complete course description\",\"published\":true}"))
            .andExpect(status().isOk());
    }
}
