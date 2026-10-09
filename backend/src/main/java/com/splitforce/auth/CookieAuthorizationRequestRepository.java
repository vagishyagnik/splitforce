package com.splitforce.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

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

    public CookieAuthorizationRequestRepository(SignedCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        // Payload is HMAC-verified before deserialization, so only bytes we wrote are read back.
        return cookies.read(request, COOKIE).map(CookieAuthorizationRequestRepository::deserialize).orElse(null);
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

    private static byte[] serialize(OAuth2AuthorizationRequest req) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(req);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize OAuth2 authorization request", e);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(byte[] data) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (OAuth2AuthorizationRequest) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            return null;
        }
    }
}
