package com.splitforce.seed;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.splitforce.user.User;
import com.splitforce.user.UserRepository;

/**
 * Backfills real email addresses for members that were originally seeded with
 * placeholder emails ({@code <slug>@placeholder.splitforce.local}). Idempotent —
 * only rewrites the email when the record is still on a placeholder address.
 * Placeholder status stays {@code true} until the user actually signs in with
 * Google (that flip is handled by {@code UserService#resolveOrCreate}).
 */
@Component
@Order(3)
public class MemberEmailBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MemberEmailBackfill.class);

    private static final String PLACEHOLDER_SUFFIX = "@placeholder.splitforce.local";

    /** Seeded display name -> real email. */
    private static final Map<String, String> MAPPING = new LinkedHashMap<>();
    static {
        MAPPING.put("AGAM DAWRA",       "agamdawra01@gmail.com");
        MAPPING.put("Achint",           "achintdawra740@gmail.com");
        MAPPING.put("Varun Bajlotra",   "varunbajlotra@gmail.com");
        MAPPING.put("vikas yadav",      "vikasyadav87500@gmail.com");
        MAPPING.put("Ram",              "sriramempire16@gmail.com");
        MAPPING.put("Vaibhav Varshney", "varshney.vaibhav18@gmail.com");
        MAPPING.put("TANMAYA YADAV",    "yadavtanmaya@gmail.com");
        MAPPING.put("Ashish jha",       "jhaash98@gmail.com");
        MAPPING.put("vishesh jain",     "visheshjain_2k18co391@dtu.ac.in");
    }

    private final UserRepository users;

    public MemberEmailBackfill(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Map.Entry<String, String> entry : MAPPING.entrySet()) {
            String displayName = entry.getKey();
            String realEmail = entry.getValue().trim().toLowerCase(Locale.ROOT);
            String placeholderEmail = placeholderEmailFor(displayName);
            try {
                users.findByEmail(placeholderEmail).ifPresentOrElse(
                        u -> updateEmail(u, realEmail, displayName),
                        () -> logNotFound(displayName, placeholderEmail, realEmail));
            } catch (Exception e) {
                log.warn("Email backfill failed for '{}': {}", displayName, e.getMessage());
            }
        }
    }

    private void updateEmail(User user, String realEmail, String displayName) {
        String current = user.getEmail() == null ? "" : user.getEmail();
        if (current.equalsIgnoreCase(realEmail)) {
            log.info("Email backfill: '{}' already on {}", displayName, realEmail);
            return;
        }
        if (!current.endsWith(PLACEHOLDER_SUFFIX)) {
            log.info("Email backfill: '{}' has non-placeholder email '{}' — leaving untouched",
                    displayName, current);
            return;
        }
        user.setEmail(realEmail);
        users.save(user);
        log.info("Email backfill: '{}' -> {}", displayName, realEmail);
    }

    private void logNotFound(String displayName, String placeholderEmail, String realEmail) {
        // Second chance: maybe the user was already migrated in a previous run.
        users.findByEmail(realEmail).ifPresentOrElse(
                u -> log.info("Email backfill: '{}' already on {}", displayName, realEmail),
                () -> log.info("Email backfill: no user found for '{}' (placeholder {} nor real {})",
                        displayName, placeholderEmail, realEmail));
    }

    private static String placeholderEmailFor(String displayName) {
        String slug = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        return slug + PLACEHOLDER_SUFFIX;
    }
}
