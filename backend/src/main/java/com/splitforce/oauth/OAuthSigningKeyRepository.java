package com.splitforce.oauth;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class OAuthSigningKeyRepository {

    private final DynamoDbTable<OAuthSigningKey> table;

    public OAuthSigningKeyRepository(DynamoDbTable<OAuthSigningKey> oauthKeysTable) {
        this.table = oauthKeysTable;
    }

    public Optional<OAuthSigningKey> findActive() {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(OAuthSigningKey.ACTIVE).build()));
    }

    /**
     * Writes the key only if none exists yet. Returns true if this caller won the race; a false
     * return means another instance already bootstrapped the key and the caller should re-read.
     */
    public boolean putIfAbsent(OAuthSigningKey key) {
        try {
            table.putItem(PutItemEnhancedRequest.builder(OAuthSigningKey.class)
                    .item(key)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(id)")
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
