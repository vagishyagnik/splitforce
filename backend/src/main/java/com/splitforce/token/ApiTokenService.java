package com.splitforce.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.splitforce.user.User;
import com.splitforce.user.UserRepository;

/**
 * Personal access tokens for API clients such as the MCP server. A token looks like
 * {@code sf_<userId hex>_<secret>}: the user id finds the user item with a GetItem, and the
 * whole token's SHA-256 is matched against the hashes stored on it.
 */
@Service
public class ApiTokenService {

    public static final String PREFIX = "sf_";
    public static final int MAX_TOKENS_PER_USER = 10;
    private static final int NAME_MAX_LEN = 60;
    private static final Pattern FORMAT = Pattern.compile("sf_([0-9a-f]{32})_[A-Za-z0-9_-]{43}");
    /** lastUsedAt is informational; writing it at most hourly keeps token use from costing a write per request. */
    private static final Duration LAST_USED_GRANULARITY = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository users;

    public ApiTokenService(UserRepository users) {
        this.users = users;
    }

    public record Created(ApiToken token, String secret) {}

    public Created create(User user, String rawName, Integer expiresInDays) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("name is required");
        if (name.length() > NAME_MAX_LEN) throw new IllegalArgumentException("name must be " + NAME_MAX_LEN + " characters or fewer");
        if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > 365)) {
            throw new IllegalArgumentException("expiresInDays must be between 1 and 365");
        }
        List<ApiToken> tokens = live(user);
        if (tokens.size() >= MAX_TOKENS_PER_USER) {
            throw new IllegalArgumentException("at most " + MAX_TOKENS_PER_USER + " tokens; revoke one first");
        }

        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String secret = PREFIX + user.getUserId().replace("-", "") + "_" + B64.encodeToString(secretBytes);

        Instant now = Instant.now();
        ApiToken t = new ApiToken();
        t.setId(HexFormat.of().formatHex(randomBytes(6)));
        t.setName(name);
        t.setHash(sha256(secret));
        t.setHint(secret.substring(secret.length() - 4));
        t.setCreatedAt(now.toString());
        if (expiresInDays != null) t.setExpiresAt(now.plus(Duration.ofDays(expiresInDays)).toString());
        tokens.add(t);
        user.setApiTokens(tokens);
        users.save(user);
        return new Created(t, secret);
    }

    /** Unexpired tokens, oldest first. */
    public List<ApiToken> list(User user) {
        return live(user);
    }

    public boolean revoke(User user, String id) {
        List<ApiToken> tokens = user.getApiTokens() == null ? new ArrayList<>() : new ArrayList<>(user.getApiTokens());
        boolean removed = tokens.removeIf(t -> t.getId().equals(id));
        if (removed) {
            user.setApiTokens(tokens.isEmpty() ? null : tokens);
            users.save(user);
        }
        return removed;
    }

    /** Returns the token's owner if the token is well-formed, known and unexpired. */
    public Optional<User> authenticate(String secret) {
        if (secret == null) return Optional.empty();
        Matcher m = FORMAT.matcher(secret);
        if (!m.matches()) return Optional.empty();
        String hex = m.group(1);
        String userId = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
        Optional<User> found = users.findById(userId);
        if (found.isEmpty() || found.get().getApiTokens() == null) return Optional.empty();
        User user = found.get();

        byte[] given = sha256(secret).getBytes(StandardCharsets.US_ASCII);
        Instant now = Instant.now();
        for (ApiToken t : user.getApiTokens()) {
            if (t.getHash() == null || !MessageDigest.isEqual(given, t.getHash().getBytes(StandardCharsets.US_ASCII))) continue;
            if (expired(t, now)) return Optional.empty();
            if (t.getLastUsedAt() == null
                    || Instant.parse(t.getLastUsedAt()).plus(LAST_USED_GRANULARITY).isBefore(now)) {
                t.setLastUsedAt(now.toString());
                users.save(user);
            }
            return Optional.of(user);
        }
        return Optional.empty();
    }

    private static List<ApiToken> live(User user) {
        List<ApiToken> out = new ArrayList<>();
        if (user.getApiTokens() == null) return out;
        Instant now = Instant.now();
        for (ApiToken t : user.getApiTokens()) if (!expired(t, now)) out.add(t);
        return out;
    }

    private static boolean expired(ApiToken t, Instant now) {
        return t.getExpiresAt() != null && !Instant.parse(t.getExpiresAt()).isAfter(now);
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
