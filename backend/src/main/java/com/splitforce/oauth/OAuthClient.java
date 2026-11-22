package com.splitforce.oauth;

import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * An OAuth client registered via Dynamic Client Registration (RFC 7591). MCP clients are public
 * (no secret) and prove possession of the authorization code with PKCE, so no secret is stored.
 */
@DynamoDbBean
public class OAuthClient {

    private String clientId;
    private String clientName;
    private List<String> redirectUris;
    private String scope;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public List<String> getRedirectUris() { return redirectUris; }
    public void setRedirectUris(List<String> redirectUris) { this.redirectUris = redirectUris; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
