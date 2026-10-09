package com.splitforce.user;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.splitforce.storage.BlobStore;

@Component
public class ProfilePictureStore {

    private static final String PREFIX = "profile-pics/";

    private final BlobStore store;

    public ProfilePictureStore(BlobStore store) {
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

    public boolean exists(String userId) {
        if (userId == null || userId.contains("/") || userId.contains("..")) return false;
        return store.exists(PREFIX + userId);
    }
}
