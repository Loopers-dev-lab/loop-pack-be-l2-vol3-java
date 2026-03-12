package com.loopers.infrastructure.persistence.jpa.point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 사용자 포인트 JPA 엔티티.
 */
@Entity
@Table(
    name = "user_points",
    indexes = {
        @Index(name = "idx_user_points_user_id", columnList = "user_id", unique = true)
    }
)
public class UserPointJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected UserPointJpaEntity() {}

    public UserPointJpaEntity(Long userId, Long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    @PrePersist
    private void prePersist() {
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBalance() {
        return balance;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
