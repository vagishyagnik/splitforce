package com.splitforce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.splitforce.expense.Expense;
import com.splitforce.group.Group;
import com.splitforce.group.GroupMember;
import com.splitforce.user.User;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class DynamoDbTables {

    public static final String USERS = "users";
    public static final String GROUPS = "groups";
    public static final String GROUP_MEMBERS = "group_members";
    public static final String EXPENSES = "expenses";

    private final String prefix;
    private final DynamoDbEnhancedClient enhanced;

    public DynamoDbTables(DynamoDbEnhancedClient enhanced, DynamoDbProperties props) {
        this.enhanced = enhanced;
        this.prefix = props.getTablePrefix() == null ? "" : props.getTablePrefix();
    }

    public String tableName(String base) { return prefix + base; }

    @Bean
    public DynamoDbTable<User> usersTable() {
        return enhanced.table(tableName(USERS), TableSchema.fromBean(User.class));
    }

    @Bean
    public DynamoDbTable<Group> groupsTable() {
        return enhanced.table(tableName(GROUPS), TableSchema.fromBean(Group.class));
    }

    @Bean
    public DynamoDbTable<GroupMember> groupMembersTable() {
        return enhanced.table(tableName(GROUP_MEMBERS), TableSchema.fromBean(GroupMember.class));
    }

    @Bean
    public DynamoDbTable<Expense> expensesTable() {
        return enhanced.table(tableName(EXPENSES), TableSchema.fromBean(Expense.class));
    }
}
