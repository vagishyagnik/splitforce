package com.splitforce.token;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.splitforce.user.User;
import com.splitforce.user.UserService;

/** Token management. Signed-in browser sessions only: a token can't mint or revoke tokens. */
@RestController
@RequestMapping("/api/me/tokens")
public class ApiTokenController {

    private final UserService userService;
    private final ApiTokenService tokens;

    public ApiTokenController(UserService userService, ApiTokenService tokens) {
        this.userService = userService;
        this.tokens = tokens;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        return tokens.list(sessionUser(auth)).stream().map(ApiTokenController::toBody).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(Authentication auth, @RequestBody CreateTokenRequest req) {
        User me = sessionUser(auth);
        ApiTokenService.Created created;
        try {
            created = tokens.create(me, req == null ? null : req.name, req == null ? null : req.expiresInDays);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        Map<String, Object> body = toBody(created.token());
        body.put("token", created.secret());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(Authentication auth, @PathVariable("id") String id) {
        if (!tokens.revoke(sessionUser(auth), id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "token not found");
        return ResponseEntity.noContent().build();
    }

    private User sessionUser(Authentication auth) {
        if (ApiTokenAuthenticationFilter.isTokenAuth(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tokens can only be managed from a signed-in browser");
        }
        return userService.resolveOrCreate((OAuth2User) auth.getPrincipal());
    }

    private static Map<String, Object> toBody(ApiToken t) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", t.getId());
        body.put("name", t.getName());
        body.put("hint", t.getHint());
        body.put("createdAt", t.getCreatedAt());
        body.put("expiresAt", t.getExpiresAt());
        body.put("lastUsedAt", t.getLastUsedAt());
        return body;
    }

    public static class CreateTokenRequest {
        public String name;
        public Integer expiresInDays;
    }
}
