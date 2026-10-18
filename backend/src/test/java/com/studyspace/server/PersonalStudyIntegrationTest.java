package com.studyspace.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class PersonalStudyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    String register(String name) throws Exception {
        return "Bearer " + json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\""+name+"\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    String upload(long version, String key) {
        return "{\"expectedVersion\":"+version+",\"requestId\":\""+key+"\",\"data\":{\"plans\":[{\"id\":1,\"title\":\"SQL\",\"category\":\"Database\",\"description\":\"\",\"targetMinutes\":25,\"elapsedTimeSeconds\":1500,\"isCompleted\":true,\"dueDate\":1000,\"isSmartReminderEnabled\":false,\"reminderHour\":20,\"reminderMinute\":0,\"createdAt\":500,\"completedAt\":900}],\"sessions\":[{\"id\":1,\"taskId\":1,\"taskTitle\":\"SQL\",\"durationMinutes\":25,\"sessionType\":\"focus\",\"timestamp\":1000}]}}";
    }
    @Test void isolatesAccountsAndRetriesWithoutDuplicatingSessions() throws Exception {
        String a = register("sync_owner"), b = register("sync_other");
        String body = upload(0,"sync-retry-request-001");
        mvc.perform(get("/api/personal-study")).andExpect(status().isUnauthorized());
        for (int i=0;i<2;i++) mvc.perform(put("/api/personal-study").header("Authorization",a).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.data.sessions.length()").value(1)).andExpect(jsonPath("$.data.plans[0].completedAt").value(900));
        mvc.perform(get("/api/personal-study").header("Authorization",b)).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.data.sessions").isEmpty());
    }
    @Test void refusesStaleDeviceAndRetainsPriorRevision() throws Exception {
        String token = register("sync_conflict");
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(upload(0,"sync-conflict-request-001"))).andExpect(status().isOk());
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(upload(0,"sync-conflict-request-002"))).andExpect(status().isConflict());
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(upload(1,"sync-conflict-request-003"))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM personal_study_snapshots WHERE user_id=(SELECT id FROM accounts WHERE username='sync_conflict')",Integer.class));
    }
    @Test void rejectsInvalidDurationsAndDuplicateIds() throws Exception {
        String token=register("sync_invalid");
        var body=json.readTree(upload(0,"sync-invalid-request-001"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.path("data").path("sessions").get(0)).put("durationMinutes",-1);
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        var inconsistent=json.readTree(upload(0,"sync-invalid-request-003"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)inconsistent.path("data").path("plans").get(0)).put("isCompleted",false);
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(inconsistent.toString())).andExpect(status().isBadRequest());
        var negativeTimestamp=json.readTree(upload(0,"sync-invalid-request-004"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)negativeTimestamp.path("data").path("plans").get(0)).put("completedAt",-1);
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(negativeTimestamp.toString())).andExpect(status().isBadRequest());
        var duplicate=json.readTree(upload(0,"sync-invalid-request-002"));
        var sessions=(com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("data").path("sessions");
        sessions.add(sessions.get(0).deepCopy());
        mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(duplicate.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/personal-study").header("Authorization",token)).andExpect(jsonPath("$.version").value(0));
    }
    @Test void twoDevicesCannotBothWriteTheSameVersion() throws Exception {
        String token=register("sync_parallel");
        var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> { start.await(); return mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(upload(0,"parallel-request-001"))).andReturn().getResponse().getStatus(); });
            var second=pool.submit(() -> { start.await(); return mvc.perform(put("/api/personal-study").header("Authorization",token).contentType("application/json").content(upload(0,"parallel-request-002"))).andReturn().getResponse().getStatus(); });
            start.countDown();
            var statuses=new java.util.ArrayList<>(java.util.List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(statuses);
            assertEquals(java.util.List.of(200,409),statuses);
        } finally { pool.shutdownNow(); }
    }
}
