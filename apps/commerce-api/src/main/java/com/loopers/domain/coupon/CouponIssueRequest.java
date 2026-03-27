package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_issue_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_user", columnNames = {"coupon_id", "user_id"})
})
@Getter
public class CouponIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    protected CouponIssueRequest() {
    }

    private CouponIssueRequest(String eventId, Long couponId, Long userId) {
        this.eventId = eventId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatus.PENDING;
        this.createdAt = ZonedDateTime.now();
    }

    public static CouponIssueRequest create(String eventId, Long couponId, Long userId) {
        return new CouponIssueRequest(eventId, couponId, userId);
    }

    public boolean isPending() {
        return this.status == CouponIssueStatus.PENDING;
    }
}
