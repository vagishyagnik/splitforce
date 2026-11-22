package com.splitforce.user;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.splitforce.storage.BlobStore;
import com.splitforce.storage.ImageTypes;

@RestController
public class UserController {

    private static final long MAX_UPLOAD_BYTES = 4 * 1024 * 1024L;

    private final UserService userService;
    private final ProfilePictureStore pictureStore;
    private final PaymentQrStore paymentQrStore;

    public UserController(UserService userService, ProfilePictureStore pictureStore, PaymentQrStore paymentQrStore) {
        this.userService = userService;
        this.pictureStore = pictureStore;
        this.paymentQrStore = paymentQrStore;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(toBody(userService.resolveOrCreate(principal)));
    }

    @PatchMapping("/api/me")
    public ResponseEntity<Map<String, Object>> updateMe(@AuthenticationPrincipal OAuth2User principal,
                                                       @RequestBody Map<String, String> body) {
        if (principal == null) return ResponseEntity.status(401).build();
        User me = userService.resolveOrCreate(principal);
        try {
            if (body.containsKey("name")) {
                userService.updateName(me, body.get("name"));
            }
            if (body.containsKey("upiId")) {
                userService.updateUpiId(me, body.get("upiId"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(toBody(me));
    }

    @PostMapping(value = "/api/me/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPicture(@AuthenticationPrincipal OAuth2User principal,
                                                             @RequestParam("file") MultipartFile file) throws IOException {
        if (principal == null) return ResponseEntity.status(401).build();
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "empty file"));
        if (file.getSize() > MAX_UPLOAD_BYTES) return ResponseEntity.badRequest().body(Map.of("error", "file too large (4MB max)"));
        String ct = ImageTypes.normalize(file.getContentType());
        if (!ImageTypes.isAllowed(ct)) return ResponseEntity.badRequest().body(Map.of("error", "must be a jpeg, png, webp or heic image"));

        User me = userService.resolveOrCreate(principal);
        pictureStore.save(me.getUserId(), file.getInputStream(), file.getSize(), ct);
        userService.applyPictureUpload(me, ct);
        return ResponseEntity.ok(toBody(me));
    }

    @GetMapping("/pictures/{userId}")
    public ResponseEntity<Resource> serve(@PathVariable String userId,
                                          @RequestParam(name = "v", required = false) String version) throws IOException {
        Optional<BlobStore.Blob> blob = pictureStore.load(userId);
        if (blob.isEmpty()) return ResponseEntity.notFound().build();
        BlobStore.Blob b = blob.get();
        // Anything stored before uploads were restricted (e.g. SVG) is served as an opaque download.
        MediaType type = ImageTypes.isAllowed(b.contentType())
                ? MediaType.parseMediaType(b.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        // Versioned URLs (?v=<pictureUpdatedAt>) change on every upload, so they can be cached forever.
        CacheControl cache = version != null
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        ResponseEntity.BodyBuilder res = ResponseEntity.ok()
                .contentType(type)
                .cacheControl(cache)
                .header("Content-Security-Policy", ImageTypes.BLOB_CSP);
        if (b.size() >= 0) res.contentLength(b.size());
        return res.body(new InputStreamResource(b.stream()));
    }

    @PostMapping(value = "/api/me/payment-qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPaymentQr(@AuthenticationPrincipal OAuth2User principal,
                                                               @RequestParam("file") MultipartFile file) throws IOException {
        if (principal == null) return ResponseEntity.status(401).build();
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "empty file"));
        if (file.getSize() > MAX_UPLOAD_BYTES) return ResponseEntity.badRequest().body(Map.of("error", "file too large (4MB max)"));
        String ct = ImageTypes.normalize(file.getContentType());
        if (!ImageTypes.isAllowed(ct)) return ResponseEntity.badRequest().body(Map.of("error", "must be a jpeg, png, webp or heic image"));

        User me = userService.resolveOrCreate(principal);
        paymentQrStore.save(me.getUserId(), file.getInputStream(), file.getSize(), ct);
        userService.applyPaymentQrUpload(me, ct);
        return ResponseEntity.ok(toBody(me));
    }

    @DeleteMapping("/api/me/payment-qr")
    public ResponseEntity<Map<String, Object>> deletePaymentQr(@AuthenticationPrincipal OAuth2User principal) throws IOException {
        if (principal == null) return ResponseEntity.status(401).build();
        User me = userService.resolveOrCreate(principal);
        paymentQrStore.delete(me.getUserId());
        userService.clearPaymentQr(me);
        return ResponseEntity.ok(toBody(me));
    }

    @GetMapping("/payment-qr/{userId}")
    public ResponseEntity<Resource> servePaymentQr(@PathVariable String userId,
                                                   @RequestParam(name = "v", required = false) String version) throws IOException {
        Optional<BlobStore.Blob> blob = paymentQrStore.load(userId);
        if (blob.isEmpty()) return ResponseEntity.notFound().build();
        BlobStore.Blob b = blob.get();
        MediaType type = ImageTypes.isAllowed(b.contentType())
                ? MediaType.parseMediaType(b.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        CacheControl cache = version != null
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        ResponseEntity.BodyBuilder res = ResponseEntity.ok()
                .contentType(type)
                .cacheControl(cache)
                .header("Content-Security-Policy", ImageTypes.BLOB_CSP);
        if (b.size() >= 0) res.contentLength(b.size());
        return res.body(new InputStreamResource(b.stream()));
    }

    private Map<String, Object> toBody(User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", user.getUserId());
        body.put("email", user.getEmail());
        body.put("name", user.getName());
        body.put("upiId", userService.plainUpiId(user));
        body.put("picture", userService.resolvePictureUrl(user));
        body.put("paymentQr", userService.resolvePaymentQrUrl(user));
        return body;
    }
}
