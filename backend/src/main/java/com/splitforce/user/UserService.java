package com.splitforce.user;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final long MAX_EXTERNAL_PICTURE_BYTES = 5L * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final UserRepository users;
    private final ProfilePictureStore pictureStore;

    public UserService(UserRepository users, ProfilePictureStore pictureStore) {
        this.users = users;
        this.pictureStore = pictureStore;
    }

    public User resolveOrCreate(OAuth2User principal) {
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        String picture = principal.getAttribute("picture");
        if (email == null) {
            throw new IllegalStateException("OAuth2 principal has no email attribute");
        }
        User user = users.findByEmail(email).map(existing -> {
            boolean changed = false;
            if (existing.isPlaceholder()) { existing.setPlaceholder(false); changed = true; }
            if (isBlank(existing.getName()) && name != null) { existing.setName(name); changed = true; }
            boolean noLocalPicture = existing.getPictureContentType() == null;
            if (noLocalPicture && isBlank(existing.getPicture()) && picture != null) {
                existing.setPicture(picture);
                changed = true;
            }
            if (changed) users.save(existing);
            return existing;
        }).orElseGet(() -> {
            User u = new User();
            u.setUserId(deterministicId(email));
            u.setEmail(email);
            u.setName(name);
            u.setPicture(picture);
            u.setPlaceholder(false);
            u.setCreatedAt(Instant.now().toString());
            users.save(u);
            return u;
        });
        syncExternalPicture(user);
        return user;
    }

    private void syncExternalPicture(User user) {
        if (user.getPictureContentType() != null) return;
        String url = user.getPicture();
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                log.warn("External picture fetch for {} returned HTTP {}", user.getEmail(), resp.statusCode());
                return;
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0) return;
            if (body.length > MAX_EXTERNAL_PICTURE_BYTES) {
                log.warn("External picture for {} exceeds size cap ({} bytes); skipping", user.getEmail(), body.length);
                return;
            }
            String contentType = resp.headers().firstValue("content-type")
                    .filter(ct -> ct.startsWith("image/"))
                    .orElse("image/jpeg");
            pictureStore.save(user.getUserId(), new ByteArrayInputStream(body), body.length, contentType);
            user.setPictureContentType(contentType);
            user.setPictureUpdatedAt(Instant.now().toEpochMilli());
            user.setPicture(null);
            users.save(user);
            log.info("Persisted external Google picture for {}", user.getEmail());
        } catch (Exception ex) {
            log.warn("External picture sync failed for {}: {}", user.getEmail(), ex.toString());
        }
    }

    public User updateName(User user, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("name must not be blank");
        if (trimmed.length() > 60) trimmed = trimmed.substring(0, 60);
        user.setName(trimmed);
        users.save(user);
        return user;
    }

    public User applyPictureUpload(User user, String contentType) {
        user.setPictureContentType(contentType);
        user.setPictureUpdatedAt(Instant.now().toEpochMilli());
        user.setPicture(null);
        users.save(user);
        return user;
    }

    public User findOrCreateMember(String displayName, String email) {
        String cleanName = displayName == null ? "" : displayName.trim();
        String cleanEmail = isBlank(email) ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (cleanName.isEmpty() && cleanEmail.isEmpty()) {
            throw new IllegalArgumentException("member requires a name or an email");
        }
        String resolvedEmail = cleanEmail.isEmpty() ? placeholderEmail(cleanName) : cleanEmail;
        String resolvedName = cleanName.isEmpty() ? nameFromEmail(cleanEmail) : cleanName;
        return users.findByEmail(resolvedEmail).orElseGet(() -> {
            User u = new User();
            u.setUserId(deterministicId(resolvedEmail));
            u.setEmail(resolvedEmail);
            u.setName(resolvedName);
            u.setPlaceholder(resolvedEmail.endsWith("@placeholder.splitforce.local"));
            u.setCreatedAt(Instant.now().toString());
            users.save(u);
            return u;
        });
    }

    private static String nameFromEmail(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        String cleaned = local.replaceAll("[._+-]+", " ").trim();
        if (cleaned.isEmpty()) return email;
        StringBuilder out = new StringBuilder(cleaned.length());
        boolean capNext = true;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isWhitespace(c)) {
                out.append(' ');
                capNext = true;
            } else if (capNext) {
                out.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String resolvePictureUrl(User user) {
        if (user == null) return null;
        if (user.getPictureContentType() != null) {
            long v = user.getPictureUpdatedAt() == null ? 0L : user.getPictureUpdatedAt();
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
                return ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/pictures/")
                        .path(user.getUserId())
                        .query("v=" + v)
                        .toUriString();
            }
            return "/pictures/" + user.getUserId() + "?v=" + v;
        }
        return user.getPicture();
    }

    private static String placeholderEmail(String displayName) {
        String slug = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        return slug + "@placeholder.splitforce.local";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String deterministicId(String email) {
        return UUID.nameUUIDFromBytes(("user:" + email).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
