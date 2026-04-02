package com.loopers.infrastructure.persistence.jpa.fcfs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 발급된 선착순 쿠폰 JPA 엔티티.
 */
@Entity
@Table(
    name = "fcfs_issued_coupon",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_fcfs_issued_coupon_coupon_id", columnList = "coupon_id")
    }
)
public class FcfsIssuedCouponJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected FcfsIssuedCouponJpaEntity() {}

    public FcfsIssuedCouponJpaEntity(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
    }

    @PrePersist
    private void prePersist() {
        this.issuedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
