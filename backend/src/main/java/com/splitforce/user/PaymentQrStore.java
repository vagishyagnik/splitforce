package com.splitforce.user;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.splitforce.storage.BlobStore;

/** Stores each user's payment QR code image (e.g. a UPI / PayTM collect QR) so group-mates can scan it to pay them back. */
@Component
public class PaymentQrStore {

    private static final String PREFIX = "payment-qr/";

    private final BlobStore store;

    public PaymentQrStore(BlobStore store) {
        this.store = store;
    }

    public void save(String userId, InputStream data, long size, String contentType) throws IOException {
        if (userId == null || userId.contains("/") || userId.contains("..")) {
            throw new IOException("invalid userId");
        }
        store.put(PREFIX + userId, data, size, contentType);
    }

    public Optional<BlobStore.Blob> load(String userId) throws IOException {
        if (userId == null || userId.contains("/") || userId.contains("..")) return Optional.empty();
        return store.get(PREFIX + userId);
    }

    public void delete(String userId) throws IOException {
        if (userId == null || userId.contains("/") || userId.contains("..")) return;
        store.delete(PREFIX + userId);
    }

    public boolean exists(String userId) {
        if (userId == null || userId.contains("/") || userId.contains("..")) return false;
        return store.exists(PREFIX + userId);
    }
}
