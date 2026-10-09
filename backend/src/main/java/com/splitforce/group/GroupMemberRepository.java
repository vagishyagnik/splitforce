package com.splitforce.group;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class GroupMemberRepository {

    private final DynamoDbTable<GroupMember> table;

    public GroupMemberRepository(DynamoDbTable<GroupMember> groupMembersTable) {
        this.table = groupMembersTable;
    }

    public void save(GroupMember member) {
        table.putItem(member);
    }

    public List<GroupMember> listByGroup(String groupId) {
        return table
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(groupId).build()))
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public void delete(String groupId, String userId) {
        table.deleteItem(Key.builder().partitionValue(groupId).sortValue(userId).build());
    }

    public List<GroupMember> listByUser(String userId) {
        DynamoDbIndex<GroupMember> index = table.index(GroupMember.USER_GROUPS_INDEX);
        return index
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}
