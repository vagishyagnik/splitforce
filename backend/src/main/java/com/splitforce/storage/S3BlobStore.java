package com.splitforce.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3BlobStore implements BlobStore {

    private final S3Client client;
    private final String bucket;
    private final String prefix;

    public S3BlobStore(S3Client client, String bucket, String prefix) {
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix;
    }

    private String fullKey(String key) {
        if (key == null || key.contains("..")) throw new IllegalArgumentException("bad key");
        return prefix.isEmpty() ? key : prefix + (prefix.endsWith("/") ? "" : "/") + key;
    }

    @Override
    public void put(String key, InputStream data, long size, String contentType) throws IOException {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fullKey(key))
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .contentLength(size)
                    .build(), RequestBody.fromInputStream(data, size));
        } catch (S3Exception e) {
            throw new IOException("s3 put failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public Optional<Blob> get(String key) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> in = client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fullKey(key))
                    .build());
            GetObjectResponse r = in.response();
            return Optional.of(new Blob(in, r.contentLength() == null ? -1 : r.contentLength(), r.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            throw new IOException("s3 get failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(fullKey(key)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fullKey(key)).build());
        } catch (S3Exception e) {
            throw new IOException("s3 delete failed: " + e.awsErrorDetails().errorMessage(), e);
        }
    }
}
