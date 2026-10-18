package com.studyspace.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService auth;
    AuthController(AuthService auth) { this.auth=auth; }
    record Credentials(@Pattern(regexp="[A-Za-z0-9_.@-]{3,80}") @NotNull String username,
                       @NotNull @Size(min=8,max=72) String password) {}
    record PortalTicket(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{40,60}") String ticket) {}
    @PostMapping("/portal-ticket")
    Map<String,Object> portalTicket(@RequestHeader("Authorization") String token) {
        var account=auth.teacher(token);
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String ticket=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now=System.currentTimeMillis();
        auth.db.update("DELETE FROM portal_tickets WHERE expires_at<?",now);
        auth.db.update("INSERT INTO portal_tickets(ticket_hash,user_id,expires_at) VALUES(?,?,?)",auth.hash(ticket),account.id(),now+60000L);
        return Map.of("ticket",ticket,"expiresAt",now+60000L);
    }
    @PostMapping("/portal-ticket/exchange") @Transactional
    Map<String,Object> exchangePortalTicket(@Valid @RequestBody PortalTicket body) {
        long now=System.currentTimeMillis();
        var rows=auth.db.query("SELECT user_id FROM portal_tickets WHERE ticket_hash=? AND expires_at>? FOR UPDATE",(r,n)->r.getLong("user_id"),auth.hash(body.ticket()),now);
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Liên kết đăng nhập đã hết hạn hoặc đã được sử dụng.");
        long userId=rows.get(0);
        auth.db.update("DELETE FROM portal_tickets WHERE ticket_hash=?",auth.hash(body.ticket()));
        var accounts=auth.db.query("SELECT id,username,display_name,role FROM accounts WHERE id=? AND enabled=TRUE",(r,n)->new AuthService.Account(r.getLong("id"),r.getString("username"),r.getString("display_name"),r.getString("role")),userId);
        if(accounts.isEmpty()||!accounts.get(0).role().equals("TEACHER"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Tài khoản không còn quyền giảng viên.");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String session=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiresAt=now+86400000L;
        auth.db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",auth.hash(session),userId,expiresAt);
        return Map.of("token",session,"expiresAt",expiresAt,"account",accounts.get(0));
    }
    @PostMapping("/register")
    Map<String,Object> register(@Valid @RequestBody Credentials c) {
        auth.db.update("INSERT INTO accounts(username,display_name,password_hash) VALUES(?,?,?)",c.username().toLowerCase(java.util.Locale.ROOT),c.username(),auth.passwords.encode(c.password()));
        return auth.login(c.username(),c.password());
    }
    @PostMapping("/login")
    Map<String,Object> login(@Valid @RequestBody Credentials c) { return auth.login(c.username(),c.password()); }
    @GetMapping("/me")
    AuthService.Account me(@RequestHeader(value="Authorization",required=false) String token) { return auth.require(token); }
    record Profile(@NotBlank @Size(max=120) String displayName) {}
    @PutMapping("/me")
    AuthService.Account profile(@RequestHeader("Authorization") String token,@Valid @RequestBody Profile profile) {
        var user=auth.require(token);
        auth.db.update("UPDATE accounts SET display_name=? WHERE id=?",profile.displayName().trim(),user.id());
        return auth.require(token);
    }
    @PostMapping("/logout")
    Map<String,Boolean> logout(@RequestHeader("Authorization") String token) {
        auth.require(token); auth.db.update("DELETE FROM sessions WHERE token_hash=?",auth.hash(token.substring(7)));
        return Map.of("ok",true);
    }
}
