package com.splitforce.auth;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Keeps the in-flight Google authorization request (state, nonce, redirect URI) in a signed
 * cookie, so the OAuth callback can land on a different instance than the one that started it.
 */
@Component
public class CookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "SF_OAUTH";
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final SignedCookies cookies;
    private final ObjectMapper json;

    public CookieAuthorizationRequestRepository(SignedCookies cookies, ObjectMapper json) {
        this.cookies = cookies;
        this.json = json;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        // Plain JSON rather than Java serialization, so even a leaked signing key can't lead to code execution.
        return cookies.read(request, COOKIE).map(this::deserialize).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            cookies.clear(response, COOKIE);
            return;
        }
        cookies.write(response, COOKIE, serialize(authorizationRequest), MAX_AGE);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = loadAuthorizationRequest(request);
        if (existing != null) cookies.clear(response, COOKIE);
        return existing;
    }

    private byte[] serialize(OAuth2AuthorizationRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("authorizationUri", req.getAuthorizationUri());
        fields.put("clientId", req.getClientId());
        fields.put("redirectUri", req.getRedirectUri());
        fields.put("scopes", req.getScopes());
        fields.put("state", req.getState());
        fields.put("additionalParameters", req.getAdditionalParameters());
        fields.put("attributes", req.getAttributes());
        fields.put("authorizationRequestUri", req.getAuthorizationRequestUri());
        try {
            return json.writeValueAsBytes(fields);
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize OAuth2 authorization request", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(byte[] data) {
        try {
            JsonNode node = json.readTree(data);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(node.path("authorizationUri").asText())
                    .clientId(node.path("clientId").asText())
                    .redirectUri(node.path("redirectUri").asText(null))
                    .scopes(json.convertValue(node.path("scopes"), new TypeReference<Set<String>>() {}))
                    .state(node.path("state").asText(null))
                    .additionalParameters(json.convertValue(node.path("additionalParameters"),
                            new TypeReference<Map<String, Object>>() {}))
                    .attributes(json.convertValue(node.path("attributes"),
                            new TypeReference<Map<String, Object>>() {}))
                    .authorizationRequestUri(node.path("authorizationRequestUri").asText())
                    .build();
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }
}
