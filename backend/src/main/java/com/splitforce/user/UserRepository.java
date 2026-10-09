package com.splitforce.user;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class UserRepository {

    private final DynamoDbTable<User> table;

    public UserRepository(DynamoDbTable<User> usersTable) {
        this.table = usersTable;
    }

    public void save(User user) {
        table.putItem(user);
    }

    public Optional<User> findById(String userId) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(userId).build()));
    }

    public Optional<User> findByEmail(String email) {
        DynamoDbIndex<User> index = table.index(User.EMAIL_INDEX);
        return index
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
