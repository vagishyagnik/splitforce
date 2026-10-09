package com.splitforce.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.splitforce.expense.Expense;
import com.splitforce.group.Group;
import com.splitforce.group.GroupMember;
import com.splitforce.user.User;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@Component
@Order(1)
public class DynamoDbTableInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

    private final DynamoDbClient client;
    private final DynamoDbTable<User> users;
    private final DynamoDbTable<Group> groups;
    private final DynamoDbTable<GroupMember> members;
    private final DynamoDbTable<Expense> expenses;

    public DynamoDbTableInitializer(DynamoDbClient client,
                                    DynamoDbTable<User> users,
                                    DynamoDbTable<Group> groups,
                                    DynamoDbTable<GroupMember> members,
                                    DynamoDbTable<Expense> expenses) {
        this.client = client;
        this.users = users;
        this.groups = groups;
        this.members = members;
        this.expenses = expenses;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> existing = Set.copyOf(client.listTables().tableNames());
        createIfMissing(existing, users, User.EMAIL_INDEX);
        createIfMissing(existing, groups, (String) null);
        createIfMissing(existing, members, GroupMember.USER_GROUPS_INDEX);
        createIfMissing(existing, expenses, (String) null);
    }

    private <T> void createIfMissing(Set<String> existing, DynamoDbTable<T> table, String gsiName) {
        if (existing.contains(table.tableName())) {
            log.debug("Table {} already exists", table.tableName());
            return;
        }
        try {
            if (gsiName == null) {
                table.createTable();
            } else {
                EnhancedGlobalSecondaryIndex gsi = EnhancedGlobalSecondaryIndex.builder()
                        .indexName(gsiName)
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build();
                table.createTable(b -> b.globalSecondaryIndices(gsi));
            }
            log.info("Created DynamoDB table {}", table.tableName());
        } catch (ResourceInUseException e) {
            log.debug("Table {} was created concurrently", table.tableName());
        }
    }
}
