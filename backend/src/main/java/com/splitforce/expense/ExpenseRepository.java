package com.splitforce.expense;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import java.util.Optional;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class ExpenseRepository {

    private final DynamoDbTable<Expense> table;

    public ExpenseRepository(DynamoDbTable<Expense> expensesTable) {
        this.table = expensesTable;
    }

    public void save(Expense expense) {
        table.putItem(expense);
    }

    public List<Expense> listByGroup(String groupId) {
        return table
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(groupId).build()))
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public long countByGroup(String groupId) {
        return listByGroup(groupId).size();
    }

    public Optional<Expense> findById(String groupId, String expenseId) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(groupId).sortValue(expenseId).build()));
    }

    public void delete(String groupId, String expenseId) {
        table.deleteItem(Key.builder().partitionValue(groupId).sortValue(expenseId).build());
    }

    public List<Expense> scanAll() {
        return table.scan().items().stream().collect(Collectors.toList());
    }
}
