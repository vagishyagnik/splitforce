package com.splitforce.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class LocalBlobStore implements BlobStore {

    private final Path root;

    public LocalBlobStore(String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        if (key == null || key.contains("..")) throw new IllegalArgumentException("bad key");
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("bad key");
        return p;
    }

    @Override
    public void put(String key, InputStream data, long size, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Optional<Blob> get(String key) throws IOException {
        Path p = resolve(key);
        if (!Files.isRegularFile(p)) return Optional.empty();
        long size = Files.size(p);
        String ct = Files.probeContentType(p);
        return Optional.of(new Blob(Files.newInputStream(p), size, ct));
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }
}
