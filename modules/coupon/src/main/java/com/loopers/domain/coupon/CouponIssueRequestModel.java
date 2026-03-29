package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "coupon_issue_request")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class CouponIssueRequestModel {

    @Id
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CouponIssueRequestStatus status;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    public static CouponIssueRequestModel pending(String requestId, Long userId, Long couponTemplateId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId는 비어 있을 수 없습니다.");
        }
        if (userId == null || couponTemplateId == null) {
            throw new IllegalArgumentException("userId와 couponTemplateId는 null일 수 없습니다.");
        }
        CouponIssueRequestModel m = new CouponIssueRequestModel();
        m.requestId = requestId;
        m.userId = userId;
        m.couponTemplateId = couponTemplateId;
        m.status = CouponIssueRequestStatus.PENDING;
        return m;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public boolean isPending() {
        return status == CouponIssueRequestStatus.PENDING;
    }

    public void markIssued(Long issuedCouponId) {
        if (issuedCouponId == null) {
            throw new IllegalArgumentException("issuedCouponId는 null일 수 없습니다.");
        }
        this.status = CouponIssueRequestStatus.ISSUED;
        this.issuedCouponId = issuedCouponId;
    }

    public void markRejected(CouponIssueRequestStatus rejection) {
        if (rejection == null
                || rejection == CouponIssueRequestStatus.PENDING
                || rejection == CouponIssueRequestStatus.ISSUED) {
            throw new IllegalArgumentException("거절 상태만 설정할 수 있습니다.");
        }
        this.status = rejection;
        this.issuedCouponId = null;
    }
}
