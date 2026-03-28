package com.loopers.domain.coupon;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * commerce-streamer에서 쿠폰 발급 Consumer가 사용하는 엔티티.
 * commerce-api의 CouponIssueModel과 같은 테이블(coupon_issue)을 매핑한다.
 */
@Getter
@Entity
@Table(name = "coupon_issue", uniqueConstraints = {
    @UniqueConstraint(name = "uk_coupon_issue_user_coupon", columnNames = {"user_id", "coupon_id"})
})
public class CouponIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    protected CouponIssueEntity() {
    }

    public CouponIssueEntity(Long couponId, Long userId, LocalDateTime expiredAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.expiredAt = expiredAt;
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
