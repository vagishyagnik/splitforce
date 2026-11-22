package com.splitforce.oauth;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@Repository
public class OAuthAuthCodeRepository {

    private final DynamoDbTable<OAuthAuthCode> table;

    public OAuthAuthCodeRepository(DynamoDbTable<OAuthAuthCode> oauthAuthCodesTable) {
        this.table = oauthAuthCodesTable;
    }

    public void save(OAuthAuthCode code) {
        table.putItem(code);
    }

    /**
     * Atomically removes the code and returns it, or empty if it was already consumed or never
     * existed. Making redemption a delete-and-return guarantees an authorization code is single-use
     * even under concurrent token requests.
     */
    public Optional<OAuthAuthCode> redeem(String codeHash) {
        if (codeHash == null) return Optional.empty();
        return Optional.ofNullable(table.deleteItem(Key.builder().partitionValue(codeHash).build()));
    }
}
