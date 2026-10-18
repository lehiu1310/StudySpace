package com.studyspace.server;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
class AuthService {
    private static final String LEGACY_SALT = "StudySpaceSecureSalt123!";
    final JdbcTemplate db;
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    AuthService(JdbcTemplate db) { this.db = db; }
    record Account(long id, String username, String displayName, String role) {}
    String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    Account require(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ")) throw new ResponseStatusException(UNAUTHORIZED, "Vui lòng đăng nhập trực tuyến.");
        var users = db.query("SELECT a.* FROM accounts a JOIN sessions s ON s.user_id=a.id WHERE s.token_hash=? AND s.expires_at>? AND a.enabled=TRUE",
            (r,n) -> new Account(r.getLong("id"), r.getString("username"),r.getString("display_name"),r.getString("role")), hash(bearer.substring(7)),System.currentTimeMillis());
        if (users.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED,"Phiên đăng nhập đã hết hạn.");
        return users.get(0);
    }
    Account admin(String bearer) {
        var account = require(bearer);
        if (!account.role().equals("ADMIN")) throw new ResponseStatusException(FORBIDDEN,"Cần quyền quản trị.");
        return account;
    }
    Account teacher(String bearer) {
        var account = require(bearer);
        if (!account.role().equals("TEACHER")) throw new ResponseStatusException(FORBIDDEN,"Chức năng này dành cho giảng viên.");
        return account;
    }
    Map<String,Object> login(String username, String password) {
        var rows = db.queryForList("SELECT * FROM accounts WHERE username=? AND enabled=TRUE", username.toLowerCase(Locale.ROOT).trim());
        if (rows.isEmpty())
            throw new ResponseStatusException(UNAUTHORIZED,"Tên đăng nhập hoặc mật khẩu không đúng.");
        var account = rows.get(0);
        String storedHash = (String) account.get("password_hash");
        boolean bcrypt = storedHash.startsWith("$2");
        if (bcrypt ? !matchesBcrypt(password, storedHash) : !matchesLegacy(password, storedHash))
            throw new ResponseStatusException(UNAUTHORIZED,"Tên đăng nhập hoặc mật khẩu không đúng.");
        if (!bcrypt) db.update("UPDATE accounts SET password_hash=? WHERE id=?", passwords.encode(password), account.get("id"));
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiry = System.currentTimeMillis()+86400000L;
        db.update("DELETE FROM sessions WHERE expires_at<?",System.currentTimeMillis());
        db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",hash(token),rows.get(0).get("id"),expiry);
        return Map.of("token",token,"expiresAt",expiry,"account",require("Bearer "+token));
    }

    private boolean matchesBcrypt(String password, String storedHash) {
        try { return passwords.matches(password, storedHash); }
        catch (IllegalArgumentException e) { return false; }
    }

    private boolean matchesLegacy(String password, String storedHash) {
        if (storedHash.matches("(?i)[0-9a-f]{64}")) return MessageDigest.isEqual(hash(password + LEGACY_SALT).getBytes(StandardCharsets.US_ASCII), storedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
