package com.splitforce.oauth;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * A short-lived, single-use authorization code (Authorization Code + PKCE flow). Only the SHA-256
 * of the code is stored, so a leaked table row can't be redeemed. {@code expiresAt} is an epoch-second
 * TTL attribute so DynamoDB prunes stale codes automatically; redemption also checks it explicitly.
 */
@DynamoDbBean
public class OAuthAuthCode {

    private String codeHash;
    private String clientId;
    private String redirectUri;
    private String userEmail;
    private String userName;
    private String userPicture;
    private String codeChallenge;
    private String scope;
    private Long expiresAt;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPicture() { return userPicture; }
    public void setUserPicture(String userPicture) { this.userPicture = userPicture; }

    public String getCodeChallenge() { return codeChallenge; }
    public void setCodeChallenge(String codeChallenge) { this.codeChallenge = codeChallenge; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    /** Epoch seconds; configured as the DynamoDB TTL attribute. */
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
