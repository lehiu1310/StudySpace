package com.studyspace.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "studyspace.gemini-api-key=")
class GeminiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GeminiController gemini;
    @Autowired JdbcTemplate db;

    @Test
    void aiEndpointRequiresSessionAndEnforcesPerAccountRateLimit() throws Exception {
        String username = "ai_limit_" + System.nanoTime();
        String password = "StudySpace@123";
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();

        String payload = "{\"category\":\"Lập trình\",\"goal\":\"Ôn Kotlin\",\"level\":\"Cơ bản\",\"weakness\":\"Dễ quên\",\"learningStyle\":\"Thực hành\",\"timeBudgetMinutes\":25,\"deadline\":\"Hôm nay\",\"weeklyFocusMinutes\":0,\"openTaskCount\":0,\"completedTaskCount\":0,\"focusSessionCount\":0,\"recentTaskTitles\":[]}";
        mvc.perform(post("/api/ai/challenge").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized());
        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/ai/challenge").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isServiceUnavailable());
        }
        mvc.perform(post("/api/ai/challenge").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isTooManyRequests());
    }

    @Test
    void chatRequiresSessionAndFallsBackWhenServerHasNoKey() throws Exception {
        String username = "ai_chat_" + System.nanoTime();
        String password = "StudySpace@123";
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();
        String payload = "{\"question\":\"Giải thích chuẩn hóa dữ liệu\",\"lessonContext\":\"Chuẩn 1NF yêu cầu các giá trị nguyên tử.\"}";

        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/chat").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/chat").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Chưa có lượt quiz đã lưu")));
    }

    @Test
    void chatHasPerAccountRateLimit() throws Exception {
        String username = "ai_chat_limit_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();
        String payload = "{\"question\":\"Giải thích chuẩn hóa dữ liệu\"}";

        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/ai/chat").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/ai/chat").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void teacherDraftEndpointRequiresTeacherOwnedChapterAndLessonSource() throws Exception {
        String ownerName = "ai_teacher_drafts_" + System.nanoTime();
        String ownerRegistration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + ownerName + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long ownerId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, ownerName);
        db.update("UPDATE accounts SET role='TEACHER' WHERE id=?", ownerId);
        String teacherLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + ownerName + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String teacherToken = "Bearer " + json.readTree(teacherLogin).path("token").asText();
        String studentName = ownerName + "_student";
        String studentRegistration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + studentName + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String studentToken = "Bearer " + json.readTree(studentRegistration).path("token").asText();
        String courseTitle = "AI draft course " + ownerId;
        db.update("INSERT INTO courses(title,description,published,created_by) VALUES(?,?,TRUE,?)",
            courseTitle, "Course for AI draft authorization test", ownerId);
        long courseId = db.queryForObject("SELECT id FROM courses WHERE title=? AND created_by=?", Long.class, courseTitle, ownerId);
        db.update("INSERT INTO chapters(course_id,title,position) VALUES(?,?,1)", courseId, "AI draft chapter");
        long chapterId = db.queryForObject("SELECT id FROM chapters WHERE course_id=? AND title=?", Long.class, courseId, "AI draft chapter");
        String payload = "{\"chapterId\":" + chapterId + ",\"questionCount\":3,\"difficulty\":2}";

        mvc.perform(post("/api/ai/teacher/question-drafts").header("Authorization", studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/teacher/question-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isConflict());

        String topicOnlyDraft = "{\"chapterId\":" + chapterId + ",\"questionCount\":1,\"difficulty\":1,\"topic\":\"INNER JOIN\"}";
        String topicOnlyResponse = mvc.perform(post("/api/ai/teacher/question-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(topicOnlyDraft))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.questions.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.readTree(topicOnlyResponse).path("questions").get(0)
            .path("explanation").asText().contains("Bản nháp khái niệm tổng quát"));

        db.update("INSERT INTO lessons(chapter_id,title,content,position) VALUES(?,?,?,1)", chapterId,
            "Lesson source", "Khóa ngoại tạo liên kết giữa hai bảng và tham chiếu khóa chính.");
        mvc.perform(post("/api/ai/teacher/question-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.questions.length()").value(3));

        String focusedDraft = "{\"chapterId\":" + chapterId + ",\"questionCount\":1,\"difficulty\":1,\"topic\":\"INNER JOIN\"}";
        String focusedResponse = mvc.perform(post("/api/ai/teacher/question-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(focusedDraft))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.questions.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json.readTree(focusedResponse).path("questions").get(0)
            .path("question").asText().contains("INNER JOIN"));
    }

    @Test
    void teacherContentDraftsAreRoleProtectedValidateModesAndUseConfiguredGemini() throws Exception {
        String teacherName = "ai_content_teacher_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + teacherName + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long teacherId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, teacherName);
        db.update("UPDATE accounts SET role='TEACHER' WHERE id=?", teacherId);
        String teacherLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + teacherName + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String teacherToken = "Bearer " + json.readTree(teacherLogin).path("token").asText();
        String studentName = teacherName + "_student";
        String studentRegistration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + studentName + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String studentToken = "Bearer " + json.readTree(studentRegistration).path("token").asText();
        String courseDraft = "{\"mode\":\"COURSE\",\"topic\":\"Cơ sở dữ liệu\"}";

        mvc.perform(post("/api/ai/teacher/content-drafts").contentType(MediaType.APPLICATION_JSON).content(courseDraft))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(courseDraft))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"COURSE\",\"topic\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"CHAPTER\",\"topic\":\"SQL\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(courseDraft))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.chapters.length()").value(4));
    }

    @Test
    void generatedTeacherOutlineRequiresDistinctItemsWithBoundedFields() throws Exception {
        var outline = json.readTree("[{\"title\":\"Nền tảng\",\"objective\":\"Nắm khái niệm\"},{\"title\":\"Truy vấn\",\"objective\":\"Viết SQL\"},{\"title\":\"Thực hành\",\"objective\":\"Áp dụng\"}]");
        assertEquals(3, gemini.validateOutline(outline, "objective").size());
        var duplicate = json.readTree("[{\"title\":\"SQL\",\"objective\":\"Một\"},{\"title\":\"sql\",\"objective\":\"Hai\"},{\"title\":\"Ba\",\"objective\":\"Ba\"}]");
        assertThrows(ResponseStatusException.class, () -> gemini.validateOutline(duplicate, "objective"));
        var tooShort = json.readTree("[{\"title\":\"Một\",\"objective\":\"Mục tiêu\"}]");
        assertThrows(ResponseStatusException.class, () -> gemini.validateOutline(tooShort, "objective"));
    }

    @Test
    void teacherCanGenerateOfflineChapterAndLessonDrafts() throws Exception {
        String username = "ai_offline_content_" + System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk());
        long ownerId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        db.update("UPDATE accounts SET role='TEACHER' WHERE id=?", ownerId);
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(login).path("token").asText();
        db.update("INSERT INTO courses(title,description,published,created_by) VALUES(?,?,TRUE,?)",
            "Offline course " + ownerId, "Course description for an offline draft integration test", ownerId);
        long courseId = db.queryForObject("SELECT id FROM courses WHERE created_by=?", Long.class, ownerId);
        db.update("INSERT INTO chapters(course_id,title,position) VALUES(?,?,0)", courseId, "SQL nâng cao");
        long chapterId = db.queryForObject("SELECT id FROM chapters WHERE course_id=?", Long.class, courseId);

        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CHAPTER\",\"courseId\":" + courseId + ",\"topic\":\"JOIN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.lessons.length()").value(3));
        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"LESSON\",\"chapterId\":" + chapterId + ",\"topic\":\"INNER JOIN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.content").isNotEmpty()).andExpect(jsonPath("$.keyPoints.length()").value(3));
    }

    @Test
    void explainRequiresSessionRejectsCorrectAnswersAndUsesSavedExplanationOffline() throws Exception {
        String username = "ai_explain_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();
        String wrongAnswer = "{\"question\":\"Kết quả của 2 + 2?\",\"selectedAnswer\":\"5\",\"correctAnswer\":\"4\",\"selectedOption\":1,\"correctOption\":0,\"sourceExplanation\":\"Cộng hai đơn vị với hai đơn vị được bốn.\"}";

        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content(wrongAnswer))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/explain").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Câu hỏi\",\"selectedAnswer\":\"4\",\"correctAnswer\":\"4\",\"selectedOption\":0,\"correctOption\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/explain").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(wrongAnswer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers.containsString("Cộng hai đơn vị")));
    }

    @Test
    void explainHasPerAccountRateLimit() throws Exception {
        String username = "ai_explain_limit_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();
        String payload = "{\"question\":\"Kết quả của 2 + 2?\",\"selectedAnswer\":\"5\",\"correctAnswer\":\"4\",\"selectedOption\":1,\"correctOption\":0}";

        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/ai/explain").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/ai/explain").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void reviewQuizRequiresSessionAndNeedsCompletedQuizData() throws Exception {
        String username = "ai_review_empty_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();

        mvc.perform(post("/api/ai/review-quiz")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/review-quiz").header("Authorization", token))
            .andExpect(status().isConflict());
    }

    @Test
    void reviewQuizCreatesOfflinePracticeWhenAiIsUnavailable() throws Exception {
        String username = "ai_review_weak_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        long chapterId = db.queryForObject("SELECT MIN(id) FROM chapters", Long.class);
        long now = System.currentTimeMillis();
        db.update("INSERT INTO quiz_attempts(user_id,chapter_id,started_at,deadline,submitted_at,score,total) VALUES(?,?,?,?,?,?,?)",
            userId, chapterId, now - 1000, now - 500, now, 0, 1);
        int attemptsBefore = db.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE user_id=?", Integer.class, userId);

        mvc.perform(post("/api/ai/review-quiz").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.questions.length()").value(5));
        assertEquals(attemptsBefore + 1, db.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE user_id=?", Integer.class, userId));
    }

    @Test
    void aiHealthIsAdminOnlyAndDoesNotExposeKey() throws Exception {
        mvc.perform(get("/api/ai/health")).andExpect(status().isUnauthorized());
        String username = "ai_health_" + System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk());
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        String studentLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/ai/health").header("Authorization", "Bearer " + json.readTree(studentLogin).path("token").asText()))
            .andExpect(status().isForbidden());
        db.update("UPDATE accounts SET role='ADMIN' WHERE id=?", userId);
        String adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/ai/health").header("Authorization", "Bearer " + json.readTree(adminLogin).path("token").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.status").value("not_configured"))
            .andExpect(jsonPath("$.key").doesNotExist());
    }

    @Test
    void generatedQuizValidatorRequiresFiveDistinctWellFormedQuestions() throws Exception {
        ObjectNode root = json.createObjectNode();
        var questions = root.putArray("questions");
        for (int i = 0; i < 5; i++) {
            var question = questions.addObject();
            question.put("question", "Câu hỏi kiến thức số " + i + "?");
            question.putArray("options").add("Đáp án A " + i).add("Đáp án B " + i).add("Đáp án C " + i).add("Đáp án D " + i);
            question.put("correctOption", i % 4);
            question.put("explanation", "Giải thích dựa trên kiến thức trong bài học.");
        }
        assertEquals(5, gemini.validateGeneratedQuiz(root).size());

        root.withArray("questions").remove(4);
        assertThrows(ResponseStatusException.class, () -> gemini.validateGeneratedQuiz(root));

        var duplicateChoices = root.putArray("questions");
        for (int i = 0; i < 5; i++) {
            var question = duplicateChoices.addObject();
            question.put("question", "Another generated question " + i + "?");
            question.putArray("options").add("Same").add("Same").add("C").add("D");
            question.put("correctOption", 0);
            question.put("explanation", "Valid explanation.");
        }
        assertThrows(ResponseStatusException.class, () -> gemini.validateGeneratedQuiz(root));
    }

    @Test
    void reviewQuizHasPerAccountRateLimit() throws Exception {
        String username = "ai_review_limit_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();

        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/ai/review-quiz").header("Authorization", token))
                .andExpect(status().isConflict());
        }
        mvc.perform(post("/api/ai/review-quiz").header("Authorization", token))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void studyPlanRequiresSessionValidatesInputsAndUsesServerConfiguration() throws Exception {
        String username = "ai_plan_" + System.nanoTime();
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registered).get("token").asText();
        String today = java.time.LocalDate.now().toString();
        String payload = "{\"category\":\"Lập trình\",\"goal\":\"Ôn Kotlin\",\"examDate\":\"" + today + "\",\"dailyMinutes\":60}";

        mvc.perform(post("/api/ai/study-plan").contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/study-plan").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace(today, "2026-02-30")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/study-plan").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("60}", "5}")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/study-plan").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void generatedStudyPlanValidatorEnforcesDeadlineAndDailyBudget() throws Exception {
        ObjectNode root = json.createObjectNode();
        var tasks = root.putArray("tasks");
        tasks.addObject().put("title", "Ôn cú pháp Kotlin").put("description", "Viết và chạy ba ví dụ.")
            .put("duration", 30).put("dayOffset", 0);
        tasks.addObject().put("title", "Luyện collection").put("description", "Giải bài tập danh sách.")
            .put("duration", 30).put("dayOffset", 0);
        assertEquals(2, gemini.validateStudyPlan(root, 60, 14).size());

        assertThrows(ResponseStatusException.class, () -> gemini.validateStudyPlan(root, 50, 14));
        ((ObjectNode) tasks.get(1)).put("dayOffset", 15);
        assertThrows(ResponseStatusException.class, () -> gemini.validateStudyPlan(root, 60, 14));
    }

    @Test
    void generatedChallengeValidatorRejectsIncompleteOrOutOfBudgetOutput() throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("title", "Ôn chuẩn hóa dữ liệu").put("diagnosis", "Bạn cần củng cố khóa chính.")
            .put("tip", "Xác định phụ thuộc hàm trước khi tách bảng.")
            .put("exercise", "Tìm khóa của quan hệ mẫu và giải thích.")
            .put("estimatedMinutes", 20).put("difficulty", "Vừa sức")
            .put("nextAction", "Làm một bài tập về khóa ứng viên.");
        root.putArray("microPlan").add("Đọc lại định nghĩa · 5 phút").add("Tự tìm khóa · 10 phút").add("Đối chiếu lời giải · 5 phút");
        root.putArray("answerChecklist").add("Nêu đúng phụ thuộc hàm").add("Chỉ ra khóa tối thiểu").add("Giải thích từng bước");
        assertEquals(20, gemini.validateChallenge(root, 25).get("estimatedMinutes"));

        assertThrows(ResponseStatusException.class, () -> gemini.validateChallenge(root, 15));
        root.putArray("microPlan").remove(2);
        assertThrows(ResponseStatusException.class, () -> gemini.validateChallenge(root, 25));
    }

    @Test
    void generatedPlanRejectsInvalidDurationsInsteadOfSilentlyClamping() throws Exception {
        ObjectNode root = json.createObjectNode();
        var tasks = root.putArray("tasks");
        for (int i = 0; i < 3; i++) tasks.addObject().put("title", "Nhiệm vụ " + i)
            .put("description", "Hoàn thành một mục tiêu học cụ thể.").put("duration", 25);
        assertEquals(3, gemini.validatePlan(root).size());
        ((ObjectNode) tasks.get(1)).put("duration", 5);
        assertThrows(ResponseStatusException.class, () -> gemini.validatePlan(root));
    }

    @Test
    void generatedResponseRejectsBlockedAndEmptyProviderResults() throws Exception {
        assertThrows(ResponseStatusException.class, () -> gemini.extractGeneratedText(json.readTree("""
            {"promptFeedback":{"blockReason":"SAFETY"}}
            """)));
        assertThrows(ResponseStatusException.class, () -> gemini.extractGeneratedText(json.readTree("{\"candidates\":[]}")));
        assertEquals("{\"ok\":true}", gemini.extractGeneratedText(json.readTree("""
            {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\\"ok\\\":"},{"text":"true}"}]}}]}
            """)));
    }
}
