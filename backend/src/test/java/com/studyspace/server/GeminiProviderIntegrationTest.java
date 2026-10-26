package com.studyspace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GeminiProviderIntegrationTest {
    private static final String TEST_KEY = "provider-test-secret";
    private static final AtomicReference<String> requestPath = new AtomicReference<>();
    private static final AtomicReference<String> requestKey = new AtomicReference<>();
    private static final AtomicReference<String> requestBody = new AtomicReference<>();
    private static final AtomicInteger providerStatus = new AtomicInteger(200);
    private static final AtomicReference<String> providerErrorBody = new AtomicReference<>("{\"error\":{\"message\":\"provider unavailable\"}}");
    private static final String RESPONSE = """
        {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\\"title\\\":\\\"Ôn chuẩn hóa dữ liệu\\\",\\\"diagnosis\\\":\\\"Cần củng cố khóa chính.\\\",\\\"tip\\\":\\\"Xác định phụ thuộc hàm trước khi tách bảng.\\\",\\\"microPlan\\\":[\\\"Đọc định nghĩa\\\",\\\"Tìm khóa\\\",\\\"Đối chiếu\\\"],\\\"exercise\\\":\\\"Tìm khóa của quan hệ mẫu.\\\",\\\"answerChecklist\\\":[\\\"Nêu phụ thuộc\\\",\\\"Chọn khóa tối thiểu\\\",\\\"Giải thích\\\"],\\\"estimatedMinutes\\\":20,\\\"difficulty\\\":\\\"Vừa sức\\\",\\\"nextAction\\\":\\\"Làm bài tập kế tiếp.\\\"}"}]}}]}
        """;
    private static final HttpServer gemini = startGeminiStub();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    private static HttpServer startGeminiStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1beta/models/gemini-3.8-flash:generateContent", exchange -> {
                requestPath.set(exchange.getRequestURI().toString());
                requestKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = (providerStatus.get() == 200 ? RESPONSE : providerErrorBody.get()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(providerStatus.get(), response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("studyspace.gemini-api-key", () -> TEST_KEY);
        registry.add("studyspace.gemini-api-base-url", () -> "http://127.0.0.1:" + gemini.getAddress().getPort() + "/v1beta/models");
    }

    @AfterAll
    static void stopGeminiStub() { gemini.stop(0); }

    @BeforeEach
    void clearCapturedRequest() {
        requestPath.set(null);
        requestKey.set(null);
        requestBody.set(null);
        providerStatus.set(200);
        providerErrorBody.set("{\"error\":{\"message\":\"provider unavailable\"}}");
    }

    @Test
    void providerKeyUsesHeaderAndUntrustedInputIsSeparatedFromSystemInstruction() throws Exception {
        String username = "ai_provider_" + System.nanoTime();
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registration).path("token").asText();
        String payload = """
            {"category":"Cơ sở dữ liệu","goal":"Ôn khóa chính","level":"Cơ bản","weakness":"Nhầm khóa","learningStyle":"Thực hành","timeBudgetMinutes":25,"deadline":"Tuần này","weeklyFocusMinutes":0,"openTaskCount":0,"completedTaskCount":0,"focusSessionCount":0,"recentTaskTitles":[]}
            """;

        mvc.perform(post("/api/ai/challenge").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estimatedMinutes").value(20))
            .andExpect(jsonPath("$.microPlan.length()").value(3))
            .andExpect(jsonPath("$.provider").value("gemini"));

        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", requestPath.get());
        assertEquals(TEST_KEY, requestKey.get());
        JsonNode sent = json.readTree(requestBody.get());
        assertEquals("LOW", sent.path("generationConfig").path("thinkingConfig").path("thinkingLevel").asText());
        assertEquals(4096, sent.path("generationConfig").path("maxOutputTokens").asInt());
        assertTrue(sent.path("systemInstruction").path("parts").get(0).path("text").asText().contains("dữ liệu không đáng tin cậy"));
        assertTrue(sent.path("contents").path(0).path("parts").get(0).path("text").asText().contains("Ôn khóa chính"));
        assertFalse(requestPath.get().contains(TEST_KEY));
        assertFalse(requestBody.get().contains(TEST_KEY));
    }

    @Test
    void adminHealthReportsConfiguredWithoutReturningKey() throws Exception {
        String username = "ai_health_provider_" + System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk());
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        org.springframework.test.web.servlet.MvcResult initial = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn();
        String studentToken = "Bearer " + json.readTree(initial.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(get("/api/ai/health").header("Authorization", studentToken)).andExpect(status().isForbidden());
        db.update("UPDATE accounts SET role='ADMIN' WHERE id=?", userId);
        String adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/ai/health").header("Authorization", "Bearer " + json.readTree(adminLogin).path("token").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.status").value("available"))
            .andExpect(jsonPath("$.key").doesNotExist());
    }

    @Test
    void invalidKeyAndQuotaResponsesUseOfflineChatFallback() throws Exception {
        String username = "ai_fallback_provider_" + System.nanoTime();
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(registration).path("token").asText();
        String payload = "{\"question\":\"Nên ôn gì tiếp theo?\"}";
        for (int statusCode : new int[]{401, 429, 403}) {
            providerStatus.set(statusCode);
            providerErrorBody.set(statusCode == 401
                ? "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"API key not valid\"}}"
                : statusCode == 429
                    ? "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Quota exceeded\"}}"
                    : "{\"error\":{\"status\":\"PERMISSION_DENIED\",\"message\":\"Access denied\"}}");
            mvc.perform(post("/api/ai/chat").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("offline"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Chưa có lượt quiz đã lưu")));
        }
        String challengePayload = """
            {"category":"Cơ sở dữ liệu","goal":"Ôn phần yếu","weakness":"Chuẩn hóa dữ liệu","timeBudgetMinutes":20,"recentTaskTitles":["Làm bài tập phụ thuộc hàm"]}
            """;
        mvc.perform(post("/api/ai/challenge").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(challengePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("offline"))
            .andExpect(jsonPath("$.estimatedMinutes").value(15))
            .andExpect(jsonPath("$.diagnosis").value(org.hamcrest.Matchers.containsString("Chuẩn hóa dữ liệu")))
            .andExpect(jsonPath("$.microPlan.length()").value(3));
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        db.update("UPDATE accounts SET role='ADMIN' WHERE id=?", userId);
        String adminLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/ai/health").header("Authorization", "Bearer " + json.readTree(adminLogin).path("token").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.status").value("access_denied"));
    }

    @Test
    void healthProbeDistinguishesInvalidKeyAndQuotaWithoutReturningProviderDetails() throws Exception {
        String username = "ai_health_states_" + System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk());
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        db.update("UPDATE accounts SET role='ADMIN' WHERE id=?", userId);
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(login).path("token").asText();

        providerStatus.set(400);
        providerErrorBody.set("{\"error\":{\"message\":\"API key not valid\"}}");
        mvc.perform(get("/api/ai/health").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("invalid_key"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không hợp lệ")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("API key"))))
            .andExpect(jsonPath("$.key").doesNotExist());

        providerStatus.set(429);
        providerErrorBody.set("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Quota exceeded\"}}");
        mvc.perform(get("/api/ai/health").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("quota_limited"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("hết hạn mức")));

        providerStatus.set(403);
        providerErrorBody.set("{\"error\":{\"status\":\"PERMISSION_DENIED\",\"message\":\"Your project has been denied access. Please contact support.\"}}");
        mvc.perform(get("/api/ai/health").header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("project_denied"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Project Google")))
            .andExpect(jsonPath("$.key").doesNotExist());
    }

    @Test
    void teacherAiDraftRejectsGenericPlaceholderTopicsInsteadOfInventingTitles() throws Exception {
        String username = "ai_topic_guard_" + System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk());
        long userId = db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        db.update("UPDATE accounts SET role='TEACHER' WHERE id=?", userId);
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"StudySpace@123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(login).path("token").asText();
        mvc.perform(post("/api/ai/teacher/content-drafts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CHAPTER\",\"courseId\":1,\"topic\":\"Chương tiếp theo trong lộ trình\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("tiêu đề chung chung")));
    }
}
