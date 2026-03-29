package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupon_issue_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id"}))
public class CouponIssueRequestEntity extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueRequestStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected CouponIssueRequestEntity() {
    }

    public CouponIssueRequestEntity(UUID requestId, String memberId, UUID couponId, CouponIssueRequestStatus status, String failureReason, LocalDateTime requestedAt, LocalDateTime processedAt) {
        this.requestId = requestId;
        this.memberId = memberId;
        this.couponId = couponId;
        this.status = status;
        this.failureReason = failureReason;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    public static CouponIssueRequestEntity from(CouponIssueRequest request) {
        return new CouponIssueRequestEntity(
                request.requestId(),
                request.memberId(),
                request.couponId(),
                request.status(),
                request.failureReason(),
                request.requestedAt(),
                request.processedAt()
        );
    }

    public CouponIssueRequest toDomain() {
        return new CouponIssueRequest(requestId, memberId, couponId, status, failureReason, requestedAt, processedAt);
    }

    public void updateFrom(CouponIssueRequest request) {
        this.status = request.status();
        this.failureReason = request.failureReason();
        this.processedAt = request.processedAt();
    }
}
