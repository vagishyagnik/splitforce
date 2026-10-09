package com.splitforce.storage;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public BlobStore blobStore(StorageProperties props) {
        if ("s3".equalsIgnoreCase(props.getMode())) {
            StorageProperties.S3 cfg = props.getS3();
            if (cfg.getBucket() == null || cfg.getBucket().isBlank()) {
                throw new IllegalStateException("app.storage.mode=s3 but app.storage.s3.bucket is not set");
            }
            S3ClientBuilder builder = S3Client.builder().httpClient(UrlConnectionHttpClient.create());
            if (cfg.getRegion() != null && !cfg.getRegion().isBlank()) {
                builder.region(Region.of(cfg.getRegion()));
            }
            if (cfg.getAccessKey() != null && !cfg.getAccessKey().isBlank()
                    && cfg.getSecretKey() != null && !cfg.getSecretKey().isBlank()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())));
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
            }
            if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(cfg.getEndpoint()));
                builder.forcePathStyle(true);
            }
            return new S3BlobStore(builder.build(), cfg.getBucket(), cfg.getPrefix());
        }
        return new LocalBlobStore(props.getLocal().getRoot());
    }
}
