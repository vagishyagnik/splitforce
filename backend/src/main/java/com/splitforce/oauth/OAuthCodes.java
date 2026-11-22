package com.splitforce.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Authorization-code generation/hashing and PKCE (S256) verification. */
public final class OAuthCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private OAuthCodes() {}

    /** A fresh high-entropy authorization code. */
    public static String newCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    /** SHA-256 (base64url) of a value — used so only code hashes are persisted. */
    public static String sha256(String value) {
        return B64.encodeToString(digest(value));
    }

    /**
     * RFC 7636 S256: verify that BASE64URL(SHA-256(verifier)) equals the stored challenge.
     * Constant-time comparison.
     */
    public static boolean verifyPkce(String verifier, String challenge) {
        if (verifier == null || challenge == null) return false;
        if (verifier.length() < 43 || verifier.length() > 128) return false;
        String computed = B64.encodeToString(digest(verifier));
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
