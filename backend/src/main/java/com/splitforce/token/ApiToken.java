package com.splitforce.token;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/** A personal access token, stored on its owner's user item. Only the SHA-256 of the token is kept. */
@DynamoDbBean
public class ApiToken {
    private String id;
    private String name;
    private String hash;
    /** Last few characters of the token, so the user can tell tokens apart. */
    private String hint;
    private String createdAt;
    private String expiresAt;
    private String lastUsedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(String lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
