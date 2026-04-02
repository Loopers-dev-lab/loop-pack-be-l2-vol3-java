package com.loopers.infrastructure.persistence.jpa.useraction;

import com.loopers.domain.useraction.ActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 사용자 행동 로그 JPA 엔티티.
 */
@Entity
@Table(
    name = "user_action_logs",
    indexes = {
        @Index(name = "idx_user_action_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_user_action_logs_action_type", columnList = "action_type"),
        @Index(name = "idx_user_action_logs_occurred_at", columnList = "occurred_at")
    }
)
public class UserActionLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected UserActionLogJpaEntity() {}

    public UserActionLogJpaEntity(Long userId, ActionType actionType, Long targetId, ZonedDateTime occurredAt) {
        this.userId = userId;
        this.actionType = actionType;
        this.targetId = targetId;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
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
