package com.splitforce.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.splitforce.user.ProfilePictureStore;
import com.splitforce.user.User;
import com.splitforce.user.UserRepository;

/**
 * Copies member portrait files from a local seed directory into the profile
 * picture store and marks the corresponding user records. Idempotent — skips
 * users that already have a locally uploaded picture.
 */
@Component
@Order(4)
public class ProfilePictureBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfilePictureBackfill.class);

    /** Portrait filename (case-sensitive, matches what's on disk) -> real email. */
    private static final Map<String, String> MAPPING = new LinkedHashMap<>();
    static {
        MAPPING.put("Achint.png",        "achintdawra740@gmail.com");
        MAPPING.put("Agam Dawra.png",    "agamdawra01@gmail.com");
        MAPPING.put("Ashish Jha.png",    "jhaash98@gmail.com");
        MAPPING.put("Tanmaya Yadav.png", "yadavtanmaya@gmail.com");
        MAPPING.put("V Sriram.png",      "sriramempire16@gmail.com");
        MAPPING.put("Vaibhav.png",       "varshney.vaibhav18@gmail.com");
        MAPPING.put("Varun.png",         "varunbajlotra@gmail.com");
        MAPPING.put("Vikas.png",         "vikasyadav87500@gmail.com");
        MAPPING.put("Vishesh Jain.png",  "visheshjain_2k18co391@dtu.ac.in");
    }

    private final Path seedDir;
    private final UserRepository users;
    private final ProfilePictureStore pictureStore;

    public ProfilePictureBackfill(@Value("${app.seed.profile-pics-dir:./data/seed-profile-pics}") String seedDir,
                                  UserRepository users,
                                  ProfilePictureStore pictureStore) {
        this.seedDir = Paths.get(seedDir).toAbsolutePath().normalize();
        this.users = users;
        this.pictureStore = pictureStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(seedDir)) {
            log.info("Profile picture backfill: directory {} not present; skipping", seedDir);
            return;
        }
        for (Map.Entry<String, String> entry : MAPPING.entrySet()) {
            String filename = entry.getKey();
            String email = entry.getValue();
            Path source = seedDir.resolve(filename);
            if (!Files.isRegularFile(source)) {
                log.info("Profile picture backfill: file {} missing; skipping", source);
                continue;
            }
            users.findByEmail(email).ifPresentOrElse(
                    u -> applyPicture(u, source, filename),
                    () -> log.info("Profile picture backfill: no user for {} (file {})", email, filename));
        }
    }

    private void applyPicture(User user, Path source, String filename) {
        if (user.getPictureContentType() != null) {
            log.info("Profile picture backfill: '{}' already has an uploaded picture; skipping", user.getEmail());
            return;
        }
        String contentType = contentTypeFor(filename);
        long size;
        try {
            size = Files.size(source);
        } catch (IOException e) {
            log.warn("Profile picture backfill: failed to size {}: {}", filename, e.getMessage());
            return;
        }
        try (InputStream in = Files.newInputStream(source)) {
            pictureStore.save(user.getUserId(), in, size, contentType);
        } catch (IOException e) {
            log.warn("Profile picture backfill: failed to store {} for {}: {}", filename, user.getEmail(), e.getMessage());
            return;
        }
        user.setPictureContentType(contentType);
        user.setPictureUpdatedAt(Instant.now().toEpochMilli());
        user.setPicture(null);
        users.save(user);
        log.info("Profile picture backfill: '{}' -> {}", user.getEmail(), filename);
    }

    private static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
