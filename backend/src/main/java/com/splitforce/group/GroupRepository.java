package com.splitforce.group;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@Repository
public class GroupRepository {

    private final DynamoDbTable<Group> table;

    public GroupRepository(DynamoDbTable<Group> groupsTable) {
        this.table = groupsTable;
    }

    public void save(Group group) {
        table.putItem(group);
    }

    public Optional<Group> findById(String groupId) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(groupId).build()));
    }

    public void delete(String groupId) {
        table.deleteItem(Key.builder().partitionValue(groupId).build());
    }
}
