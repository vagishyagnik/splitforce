package com.splitforce.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String mode = "local";
    private Local local = new Local();
    private S3 s3 = new S3();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Local getLocal() { return local; }
    public void setLocal(Local local) { this.local = local; }

    public S3 getS3() { return s3; }
    public void setS3(S3 s3) { this.s3 = s3; }

    public static class Local {
        private String root = "./data/blobs";
        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
    }

    public static class S3 {
        private String bucket;
        private String region;
        private String accessKey;
        private String secretKey;
        private String endpoint;
        private String prefix = "";

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
    }
}
