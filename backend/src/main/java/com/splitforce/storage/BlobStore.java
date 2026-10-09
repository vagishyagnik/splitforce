package com.splitforce.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface BlobStore {

    void put(String key, InputStream data, long size, String contentType) throws IOException;

    Optional<Blob> get(String key) throws IOException;

    boolean exists(String key);

    void delete(String key) throws IOException;

    record Blob(InputStream stream, long size, String contentType) implements AutoCloseable {
        @Override public void close() throws IOException { stream.close(); }
    }
}
