package com.studyspace.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/personal-study")
class PersonalStudyController {
    private final AuthService auth;
    private final ObjectMapper json;
    PersonalStudyController(AuthService auth, ObjectMapper json) { this.auth = auth; this.json = json; }

    record Plan(@Positive int id, @NotBlank @Size(max=500) String title,
        @NotNull @Size(max=200) String category, @NotNull @Size(max=20000) String description,
        @Min(1) @Max(1440) int targetMinutes, @Min(0) int elapsedTimeSeconds,
        boolean isCompleted, @Min(0) long dueDate, boolean isSmartReminderEnabled,
        @Min(0) @Max(23) int reminderHour, @Min(0) @Max(59) int reminderMinute,
        @Min(0) long createdAt, @Min(0) long completedAt) {}
    record Focus(@Positive int id, Integer taskId, @Size(max=500) String taskTitle,
        @Min(1) @Max(1440) int durationMinutes,
        @NotNull @Pattern(regexp="focus|break|meditation") String sessionType, @Min(0) long timestamp) {}
    record Data(@NotNull @Size(max=5000) List<@Valid Plan> plans,
        @NotNull @Size(max=20000) List<@Valid Focus> sessions) {}
    record Upload(@Min(0) long expectedVersion,
        @NotNull @Pattern(regexp="[a-zA-Z0-9-]{16,64}") String requestId, @NotNull @Valid Data data) {}

    private Map<String,Object> latest(long userId) throws Exception {
        var rows = auth.db.queryForList("SELECT version,updated_at,payload FROM personal_study_snapshots WHERE user_id=? ORDER BY version DESC LIMIT 1", userId);
        if (rows.isEmpty()) return Map.of("version", 0L, "updatedAt", 0L, "data", new Data(List.of(), List.of()));
        var row = rows.get(0);
        return Map.of("version", row.get("version"), "updatedAt", row.get("updated_at"), "data", json.readTree((String)row.get("payload")));
    }

    @GetMapping
    Map<String,Object> get(@RequestHeader(value="Authorization",required=false) String token) throws Exception {
        return latest(auth.require(token).id());
    }

    @PutMapping
    @Transactional
    Map<String,Object> put(@RequestHeader(value="Authorization",required=false) String token,
        @Valid @RequestBody Upload upload) throws Exception {
        var user = auth.require(token);
        // Lock the account even before its first snapshot exists; two devices cannot both win version 0.
        auth.db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE", Long.class, user.id());
        auth.require(token);
        var current = latest(user.id());
        long version = ((Number)current.get("version")).longValue();
        String payload = json.writeValueAsString(upload.data());
        var retries = auth.db.queryForList("SELECT version,payload FROM personal_study_snapshots WHERE user_id=? AND request_id=?", user.id(), upload.requestId());
        if (!retries.isEmpty()) {
            var prior = retries.get(0);
            if (((Number)prior.get("version")).longValue() != version || !payload.equals(prior.get("payload")))
                throw new ResponseStatusException(CONFLICT, "Dữ liệu đã thay đổi. Hãy kiểm tra phiên bản mới trước khi đồng bộ.");
            return current;
        }
        if (version != upload.expectedVersion()) throw new ResponseStatusException(CONFLICT, "Dữ liệu trên máy chủ đã thay đổi. Hãy đồng bộ lại để chọn bản cần giữ.");
        if (upload.data().plans().stream().map(Plan::id).distinct().count() != upload.data().plans().size()
            || upload.data().sessions().stream().map(Focus::id).distinct().count() != upload.data().sessions().size())
            throw new ResponseStatusException(BAD_REQUEST, "Mã dữ liệu trùng lặp.");
        if (upload.data().plans().stream().anyMatch(plan -> !plan.isCompleted() && plan.completedAt() > 0))
            throw new ResponseStatusException(BAD_REQUEST, "Thời điểm hoàn thành không khớp trạng thái kế hoạch.");
        long now = System.currentTimeMillis();
        auth.db.update("INSERT INTO personal_study_snapshots(user_id,version,request_id,payload,updated_at) VALUES(?,?,?,?,?)",
            user.id(), version + 1, upload.requestId(), payload, now);
        return latest(user.id());
    }
}
