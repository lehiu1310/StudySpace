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
class SequentialLessonIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @Test void lessonsUnlockInConfiguredOrderAndServerRejectsSkipping() throws Exception {
        var auth=mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"username\":\"sequence_student\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token="Bearer "+json.readTree(auth).get("token").asText();
        db.update("INSERT INTO courses(title,description,published) VALUES('Sequential test','Order test',TRUE)");
        long course=db.queryForObject("SELECT id FROM courses WHERE title='Sequential test'",Long.class);
        db.update("INSERT INTO chapters(course_id,title,position) VALUES(?,?,?)",course,"Chapter",1);
        long chapter=db.queryForObject("SELECT id FROM chapters WHERE course_id=?",Long.class,course);
        db.update("INSERT INTO lessons(chapter_id,title,content,material_url,position) VALUES(?,?,?,?,?)",chapter,"First","First lesson","",1);
        db.update("INSERT INTO lessons(chapter_id,title,content,material_url,position) VALUES(?,?,?,?,?)",chapter,"Second","Second lesson","",2);
        long first=db.queryForObject("SELECT id FROM lessons WHERE chapter_id=? AND position=1",Long.class,chapter);
        long second=db.queryForObject("SELECT id FROM lessons WHERE chapter_id=? AND position=2",Long.class,chapter);

        mvc.perform(get("/api/courses/"+course).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.chapters[0].lessons[0].locked").value(false))
            .andExpect(jsonPath("$.chapters[0].lessons[1].locked").value(true));
        mvc.perform(put("/api/lessons/"+second+"/complete").header("Authorization",token))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/lessons/"+first+"/complete").header("Authorization",token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/courses/"+course).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.chapters[0].lessons[1].locked").value(false));
        mvc.perform(put("/api/lessons/"+second+"/complete").header("Authorization",token))
            .andExpect(status().isOk());
    }
}
