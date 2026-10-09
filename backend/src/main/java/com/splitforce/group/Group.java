package com.splitforce.group;

import java.math.BigDecimal;
import java.util.Map;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class Group {
    private String groupId;
    private String name;
    private String type;
    private String baseCurrency;
    private String ownerUserId;
    private String createdAt;
    private Map<String, BigDecimal> fxRates;
    private String notes;
    private String notesUpdatedAt;
    private String notesUpdatedByUserId;

    @DynamoDbPartitionKey
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Map<String, BigDecimal> getFxRates() { return fxRates; }
    public void setFxRates(Map<String, BigDecimal> fxRates) { this.fxRates = fxRates; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getNotesUpdatedAt() { return notesUpdatedAt; }
    public void setNotesUpdatedAt(String notesUpdatedAt) { this.notesUpdatedAt = notesUpdatedAt; }

    public String getNotesUpdatedByUserId() { return notesUpdatedByUserId; }
    public void setNotesUpdatedByUserId(String notesUpdatedByUserId) { this.notesUpdatedByUserId = notesUpdatedByUserId; }
}
