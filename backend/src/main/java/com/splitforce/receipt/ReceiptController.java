package com.splitforce.receipt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.splitforce.group.GroupMemberRepository;
import com.splitforce.storage.BlobStore;
import com.splitforce.user.User;
import com.splitforce.user.UserService;

@RestController
public class ReceiptController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"
    );
    private static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final String PREFIX = "receipts/";

    private final UserService userService;
    private final GroupMemberRepository groupMembers;
    private final BlobStore store;

    public ReceiptController(UserService userService,
                             GroupMemberRepository groupMembers,
                             BlobStore store) {
        this.userService = userService;
        this.groupMembers = groupMembers;
        this.store = store;
    }

    @PostMapping("/api/groups/{groupId}/receipts")
    public Map<String, Object> upload(@AuthenticationPrincipal OAuth2User principal,
                                      @PathVariable("groupId") String groupId,
                                      @RequestParam("file") MultipartFile file) {
        User me = userService.resolveOrCreate(principal);
        boolean iAmMember = groupMembers.listByGroup(groupId).stream()
                .anyMatch(m -> me.getUserId().equals(m.getUserId()));
        if (!iAmMember) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a member of this group");

        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file too large (max 4MB)");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "unsupported type " + type + " — use jpeg / png / webp / heic");
        }

        String ext = extensionFor(type, file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String receiptKey = safeSegment(groupId) + "/" + filename;
        try (InputStream in = file.getInputStream()) {
            store.put(PREFIX + receiptKey, in, file.getSize(), type);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could not save receipt");
        }
        return Map.of("receiptKey", receiptKey, "url", "/receipts/" + receiptKey);
    }

    @GetMapping("/receipts/{groupId}/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable("groupId") String groupId,
                                          @PathVariable("filename") String filename) throws IOException {
        String key = PREFIX + safeSegment(groupId) + "/" + safeSegment(filename);
        Optional<BlobStore.Blob> blob = store.get(key);
        if (blob.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "receipt not found");
        BlobStore.Blob b = blob.get();
        MediaType type = b.contentType() != null
                ? MediaType.parseMediaType(b.contentType())
                : mediaTypeFor(filename);
        ResponseEntity.BodyBuilder res = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePrivate())
                .contentType(type);
        if (b.size() >= 0) res.contentLength(b.size());
        return res.body(new InputStreamResource(b.stream()));
    }

    private static String safeSegment(String s) {
        if (s == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad path");
        if (s.contains("/") || s.contains("\\") || s.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad path");
        }
        return s;
    }

    private static String extensionFor(String contentType, String filename) {
        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) return "jpg";
        if ("image/png".equals(contentType)) return "png";
        if ("image/webp".equals(contentType)) return "webp";
        if ("image/heic".equals(contentType) || "image/heif".equals(contentType)) return "heic";
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ext.matches("[a-z0-9]{1,5}")) return ext;
            }
        }
        return "bin";
    }

    private static MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) return MediaType.valueOf("image/heic");
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
