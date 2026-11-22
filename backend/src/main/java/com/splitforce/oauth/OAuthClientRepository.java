package com.splitforce.oauth;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@Repository
public class OAuthClientRepository {

    private final DynamoDbTable<OAuthClient> table;

    public OAuthClientRepository(DynamoDbTable<OAuthClient> oauthClientsTable) {
        this.table = oauthClientsTable;
    }

    public void save(OAuthClient client) {
        table.putItem(client);
    }

    public Optional<OAuthClient> findById(String clientId) {
        if (clientId == null) return Optional.empty();
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(clientId).build()));
    }
}
