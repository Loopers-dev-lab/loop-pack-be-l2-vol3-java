package com.loopers.infrastructure.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 쿠폰 발급 요청 이력 — 비동기 발급의 추적/폴링용
 *
 * API가 요청을 받으면 PENDING으로 생성하고 즉시 202 응답.
 * Consumer가 처리 완료 후 ISSUED 또는 FAILED로 업데이트.
 * 유저가 이 테이블을 폴링해서 발급 결과를 확인한다.
 */
@Entity
@Table(name = "coupon_issue_requests", indexes = {
        @Index(name = "idx_coupon_issue_req_user_template", columnList = "user_id, coupon_template_id"),
        @Index(name = "idx_coupon_issue_req_event_id", columnList = "event_id", unique = true)
})
public class CouponIssueRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    protected CouponIssueRequestEntity() {}

    public static CouponIssueRequestEntity create(Long couponTemplateId, Long userId, String eventId) {
        CouponIssueRequestEntity entity = new CouponIssueRequestEntity();
        entity.couponTemplateId = couponTemplateId;
        entity.userId = userId;
        entity.eventId = eventId;
        entity.status = CouponIssueRequestStatus.PENDING;
        entity.requestedAt = ZonedDateTime.now();
        return entity;
    }

    public void markIssued(Long issuedCouponId) {
        this.status = CouponIssueRequestStatus.ISSUED;
        this.issuedCouponId = issuedCouponId;
        this.processedAt = ZonedDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCouponTemplateId() { return couponTemplateId; }
    public Long getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public CouponIssueRequestStatus getStatus() { return status; }
    public Long getIssuedCouponId() { return issuedCouponId; }
    public String getFailureReason() { return failureReason; }
    public ZonedDateTime getRequestedAt() { return requestedAt; }
    public ZonedDateTime getProcessedAt() { return processedAt; }
}
