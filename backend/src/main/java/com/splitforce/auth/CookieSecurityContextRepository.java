package com.splitforce.auth;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stores the logged-in user in a signed cookie instead of an HttpSession. Controllers still
 * receive an {@link OAuth2User} carrying the Google {@code sub}, {@code email}, {@code name}
 * and {@code picture} attributes.
 */
@Component
public class CookieSecurityContextRepository implements SecurityContextRepository {

    static final String COOKIE = "SF_SESSION";
    private static final List<String> ATTRIBUTES = List.of("sub", "email", "name", "picture");
    private static final List<GrantedAuthority> AUTHORITIES = AuthorityUtils.createAuthorityList("OAUTH2_USER");
    private static final String EXPIRES = "exp";

    private final SignedCookies cookies;
    private final ObjectMapper json;
    private final Duration maxAge;

    public CookieSecurityContextRepository(SignedCookies cookies,
                                           ObjectMapper json,
                                           @Value("${app.auth.session-days:30}") long sessionDays) {
        this.cookies = cookies;
        this.json = json;
        this.maxAge = Duration.ofDays(sessionDays);
    }

    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Map<String, Object> claims = readClaims(holder.getRequest());
        if (claims != null) {
            Map<String, Object> attributes = new HashMap<>(claims);
            attributes.remove(EXPIRES);
            OAuth2User user = new DefaultOAuth2User(AUTHORITIES, attributes, "email");
            context.setAuthentication(new OAuth2AuthenticationToken(user, AUTHORITIES, "google"));
        }
        return context;
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = context == null ? null : context.getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken token)) {
            // Logout (or an anonymous context) clears the session cookie.
            if (containsContext(request)) cookies.clear(response, COOKIE);
            return;
        }
        Map<String, Object> claims = new HashMap<>();
        for (String name : ATTRIBUTES) {
            Object value = token.getPrincipal().getAttribute(name);
            if (value != null) claims.put(name, value.toString());
        }
        if (claims.get("email") == null) return;
        claims.put(EXPIRES, Instant.now().plus(maxAge).getEpochSecond());
        try {
            cookies.write(response, COOKIE, json.writeValueAsBytes(claims), maxAge);
        } catch (IOException e) {
            throw new IllegalStateException("could not write session cookie", e);
        }
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return cookies.read(request, COOKIE).isPresent();
    }

    private Map<String, Object> readClaims(HttpServletRequest request) {
        return cookies.read(request, COOKIE).map(bytes -> {
            try {
                Map<String, Object> claims = json.readValue(bytes, new TypeReference<Map<String, Object>>() {});
                Object exp = claims.get(EXPIRES);
                boolean live = exp instanceof Number n && n.longValue() > Instant.now().getEpochSecond();
                return live && claims.get("email") != null ? claims : null;
            } catch (IOException e) {
                return null;
            }
        }).orElse(null);
    }
}
