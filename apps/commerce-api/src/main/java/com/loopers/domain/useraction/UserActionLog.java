package com.loopers.domain.useraction;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 사용자 행동 로그 도메인 엔티티.
 */
public class UserActionLog {

    private final Long id;
    private final Long userId;
    private final ActionType actionType;
    private final Long targetId;
    private final ZonedDateTime occurredAt;
    private final ZonedDateTime createdAt;

    private UserActionLog(Long id, Long userId, ActionType actionType, Long targetId,
                          ZonedDateTime occurredAt, ZonedDateTime createdAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.createdAt = createdAt != null ? createdAt : ZonedDateTime.now();
    }

    /**
     * 새 행동 로그 생성.
     */
    public static UserActionLog create(Long userId, ActionType actionType, Long targetId, ZonedDateTime occurredAt) {
        return new UserActionLog(null, userId, actionType, targetId, occurredAt, ZonedDateTime.now());
    }

    /**
     * 영속화된 로그 재구성.
     */
    public static UserActionLog reconstitute(Long id, Long userId, ActionType actionType,
                                              Long targetId, ZonedDateTime occurredAt, ZonedDateTime createdAt) {
        return new UserActionLog(id, userId, actionType, targetId, occurredAt, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ZonedDateTime getOccurredAt() {
        return occurredAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
