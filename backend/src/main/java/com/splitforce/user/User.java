package com.splitforce.user;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@DynamoDbBean
public class User {
    public static final String EMAIL_INDEX = "email-index";

    private String userId;
    private String email;
    private String name;
    private String picture;
    private String pictureContentType;
    private Long pictureUpdatedAt;
    private boolean placeholder;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSecondaryPartitionKey(indexNames = EMAIL_INDEX)
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPicture() { return picture; }
    public void setPicture(String picture) { this.picture = picture; }

    public String getPictureContentType() { return pictureContentType; }
    public void setPictureContentType(String pictureContentType) { this.pictureContentType = pictureContentType; }

    public Long getPictureUpdatedAt() { return pictureUpdatedAt; }
    public void setPictureUpdatedAt(Long pictureUpdatedAt) { this.pictureUpdatedAt = pictureUpdatedAt; }

    public boolean isPlaceholder() { return placeholder; }
    public void setPlaceholder(boolean placeholder) { this.placeholder = placeholder; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
