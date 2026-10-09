package com.splitforce.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads and writes HMAC-signed cookies. Login state lives in these cookies instead of
 * an HttpSession so that any Lambda instance can serve any request.
 */
@Component
public class SignedCookies {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final boolean secure;

    public SignedCookies(@Value("${app.auth.secret}") String secret,
                         @Value("${app.auth.cookie-secure:false}") boolean secure) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.auth.secret must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(bytes, HMAC);
        this.secure = secure;
    }

    /** Returns the verified payload of a cookie, or empty if missing or tampered with. */
    public Optional<byte[]> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return verify(c.getValue());
        }
        return Optional.empty();
    }

    public void write(HttpServletResponse response, String name, byte[] payload, Duration maxAge) {
        String value = B64.encodeToString(payload) + "." + B64.encodeToString(sign(payload));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(name, value, maxAge).toString());
    }

    public void clear(HttpServletResponse response, String name) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(name, "", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    private Optional<byte[]> verify(String value) {
        int dot = value.indexOf('.');
        if (dot <= 0) return Optional.empty();
        try {
            byte[] payload = B64D.decode(value.substring(0, dot));
            byte[] sig = B64D.decode(value.substring(dot + 1));
            return MessageDigest.isEqual(sig, sign(payload)) ? Optional.of(payload) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
