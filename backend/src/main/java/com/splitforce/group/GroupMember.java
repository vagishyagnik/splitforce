package com.splitforce.group;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class GroupMember {
    public static final String USER_GROUPS_INDEX = "user-groups-index";

    private String groupId;
    private String userId;
    private String displayName;
    private String nickname;
    private boolean active;
    private String joinedAt;

    @DynamoDbPartitionKey
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = USER_GROUPS_INDEX)
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /** Per-group self-chosen nickname; when set it overrides the member's live account name in this group. */
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }
}
