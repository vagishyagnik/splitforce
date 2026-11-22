package com.splitforce.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authenticated symmetric encryption for sensitive fields stored at rest (e.g. UPI IDs).
 * AES-256-GCM with a random per-value nonce; ciphertext is tagged "upi1:" so plaintext
 * written before encryption existed still reads back unchanged.
 *
 * The key is derived from the app session secret with a domain-separation label, so no
 * extra secret has to be provisioned; set {@code app.crypto.secret} to rotate it apart
 * from the session secret.
 */
@Component
public class FieldCipher {

    private static final String PREFIX = "upi1:";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public FieldCipher(@Value("${app.crypto.secret:${app.auth.secret}}") String secret) {
        try {
            byte[] material = MessageDigest.getInstance("SHA-256")
                    .digest(("splitforce-field-v1:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(material, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("could not initialise field cipher", e);
        }
    }

    /** Encrypts plaintext to a "upi1:"-tagged token. Returns null for null input. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + B64.encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    /** Decrypts a token produced by {@link #encrypt}. Untagged values are returned as-is (legacy plaintext). */
    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            byte[] blob = B64D.decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(blob, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }
}
