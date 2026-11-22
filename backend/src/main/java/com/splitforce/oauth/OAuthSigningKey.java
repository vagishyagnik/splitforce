package com.splitforce.oauth;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * The RSA keypair used to sign MCP access tokens, serialized as a full JWK (including the private
 * key). Stored once and read by every Lambda instance so tokens signed by one validate on another.
 * A single row with id {@code "active"}; bootstrapped on first use with a conditional write so
 * concurrent cold starts can't create competing keys.
 */
@DynamoDbBean
public class OAuthSigningKey {

    public static final String ACTIVE = "active";

    private String id;
    private String kid;
    private String jwkJson;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKid() { return kid; }
    public void setKid(String kid) { this.kid = kid; }

    /** Full RSA JWK as JSON, including private key material. */
    public String getJwkJson() { return jwkJson; }
    public void setJwkJson(String jwkJson) { this.jwkJson = jwkJson; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
