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
 *
 * <p>Sessions expire after {@code app.auth.idle-minutes} without a request ({@link #refresh}
 * slides the deadline forward), and never outlive {@code app.auth.session-days} since login.
 */
@Component
public class CookieSecurityContextRepository implements SecurityContextRepository {

    static final String COOKIE = "SF_SESSION";
    private static final List<String> ATTRIBUTES = List.of("sub", "email", "name", "picture");
    private static final List<GrantedAuthority> AUTHORITIES = AuthorityUtils.createAuthorityList("OAUTH2_USER");
    private static final String EXPIRES = "exp";
    private static final String ISSUED = "iat";
    /** Don't rewrite the cookie on every request; once a minute is precise enough. */
    private static final long REFRESH_SLACK_SECONDS = 60;

    private final SignedCookies cookies;
    private final ObjectMapper json;
    private final Duration idle;
    private final Duration maxLifetime;

    public CookieSecurityContextRepository(SignedCookies cookies,
                                           ObjectMapper json,
                                           @Value("${app.auth.idle-minutes:60}") long idleMinutes,
                                           @Value("${app.auth.session-days:30}") long sessionDays) {
        this.cookies = cookies;
        this.json = json;
        this.idle = Duration.ofMinutes(idleMinutes);
        this.maxLifetime = Duration.ofDays(sessionDays);
    }

    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Map<String, Object> claims = readClaims(holder.getRequest());
        if (claims != null) {
            Map<String, Object> attributes = new HashMap<>(claims);
            attributes.remove(EXPIRES);
            attributes.remove(ISSUED);
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
        write(response, claims, Instant.now().getEpochSecond());
    }

    /** Slides the idle deadline of a live session cookie forward; no-op when there isn't one. */
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> claims = readClaims(request);
        if (claims == null) return;
        long now = Instant.now().getEpochSecond();
        long exp = ((Number) claims.get(EXPIRES)).longValue();
        if (exp - now > idle.toSeconds() - REFRESH_SLACK_SECONDS) return;
        write(response, claims, ((Number) claims.get(ISSUED)).longValue());
    }

    private void write(HttpServletResponse response, Map<String, Object> claims, long issuedAt) {
        long now = Instant.now().getEpochSecond();
        long exp = Math.min(now + idle.toSeconds(), issuedAt + maxLifetime.toSeconds());
        if (exp <= now) return;
        claims.put(ISSUED, issuedAt);
        claims.put(EXPIRES, exp);
        try {
            cookies.write(response, COOKIE, json.writeValueAsBytes(claims), Duration.ofSeconds(exp - now));
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
                long now = Instant.now().getEpochSecond();
                Object exp = claims.get(EXPIRES);
                // Cookies without iat predate the idle timeout (they lived 30 days); make those log in again.
                boolean live = exp instanceof Number n && n.longValue() > now
                        && n.longValue() <= now + idle.toSeconds()
                        && claims.get(ISSUED) instanceof Number;
                return live && claims.get("email") != null ? claims : null;
            } catch (IOException e) {
                return null;
            }
        }).orElse(null);
    }
}
