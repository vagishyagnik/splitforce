package com.splitforce.expense;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class Expense {
    private String groupId;
    private String expenseId;
    private String date;
    private String description;
    private String category;
    private BigDecimal cost;
    private String currency;
    private boolean payment;
    private Map<String, BigDecimal> shares = new HashMap<>();
    private String createdAt;
    private String createdByUserId;
    private String paidByUserId;
    private String notes;
    private String receiptKey;
    private String splitMode;

    @DynamoDbPartitionKey
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    @DynamoDbSortKey
    public String getExpenseId() { return expenseId; }
    public void setExpenseId(String expenseId) { this.expenseId = expenseId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isPayment() { return payment; }
    public void setPayment(boolean payment) { this.payment = payment; }

    public Map<String, BigDecimal> getShares() { return shares; }
    public void setShares(Map<String, BigDecimal> shares) { this.shares = shares; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getPaidByUserId() { return paidByUserId; }
    public void setPaidByUserId(String paidByUserId) { this.paidByUserId = paidByUserId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReceiptKey() { return receiptKey; }
    public void setReceiptKey(String receiptKey) { this.receiptKey = receiptKey; }

    public String getSplitMode() { return splitMode; }
    public void setSplitMode(String splitMode) { this.splitMode = splitMode; }
}
