package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 결과 엔티티.
 * <p>
 * 선착순 쿠폰 발급 요청의 처리 결과를 저장한다.
 * requestId를 PK로 사용하여 멱등성을 보장한다.
 * </p>
 */
@Entity
@Table(name = "coupon_issue_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueResultModel {

    @Id
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueStatus status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CouponIssueResultModel(String requestId, Long userId, Long couponId,
                                    CouponIssueStatus status, String reason) {
        this.requestId = requestId;
        this.userId = userId;
        this.couponId = couponId;
        this.status = status;
        this.reason = reason;
    }

    /**
     * 발급 성공 결과를 생성한다.
     */
    public static CouponIssueResultModel issued(String requestId, Long userId, Long couponId) {
        return new CouponIssueResultModel(requestId, userId, couponId, CouponIssueStatus.ISSUED, null);
    }

    /**
     * 발급 거부 결과를 생성한다.
     */
    public static CouponIssueResultModel rejected(String requestId, Long userId, Long couponId, String reason) {
        return new CouponIssueResultModel(requestId, userId, couponId, CouponIssueStatus.REJECTED, reason);
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
