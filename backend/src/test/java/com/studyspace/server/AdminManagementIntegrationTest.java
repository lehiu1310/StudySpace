package com.studyspace.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AdminManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    private String adminToken() throws Exception {
        var body=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"Test-only-password-123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(body).get("token").asText();
    }

    @Test void adminUserChangesAreAuditedAndAuditIsAdminOnly() throws Exception {
        String username="admin_audit_"+System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\""+username+"\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk());
        long userId=db.queryForObject("SELECT id FROM accounts WHERE username=?",Long.class,username);
        String admin=adminToken();

        mvc.perform(put("/api/admin/users/"+userId+"/role").header("Authorization",admin)
            .contentType("application/json").content("{\"role\":\"TEACHER\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/"+userId).header("Authorization",admin)
            .contentType("application/json").content("{\"enabled\":false}"))
            .andExpect(status().isOk());
        String courseTitle="Admin audit course "+System.nanoTime();
        var course=json.readTree(mvc.perform(post("/api/admin/courses").header("Authorization",admin)
            .contentType("application/json").content(json.createObjectNode().put("title",courseTitle)
                .put("description","Draft created in integration test").put("published",false).toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/admin/chapters").header("Authorization",admin).contentType("application/json")
            .content(json.createObjectNode().put("courseId",course.get("id").asLong()).put("title","Audit chapter")
                .put("position",1).toString())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit").header("Authorization",admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("CREATE_CHAPTER"))
            .andExpect(jsonPath("$[0].actor_name").exists())
            .andExpect(jsonPath("$[1].action").value("CREATE_COURSE"))
            .andExpect(jsonPath("$[2].action").value("DISABLE_USER"))
            .andExpect(jsonPath("$[3].action").value("CHANGE_ROLE"));
        mvc.perform(get("/api/admin/audit").header("Authorization","invalid"))
            .andExpect(status().isUnauthorized());
    }
}
