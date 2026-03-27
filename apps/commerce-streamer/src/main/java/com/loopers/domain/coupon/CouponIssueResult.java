package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Consumer용 CouponIssueResult 엔티티.
 * commerce-api의 CouponIssueResult과 같은 테이블을 바라본다.
 */
@Entity
@Table(name = "coupon_issue_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponIssueResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_template_id", nullable = false, updatable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public void markIssued() {
        this.status = CouponIssueStatus.ISSUED;
    }

    public void markSoldOut() {
        this.status = CouponIssueStatus.SOLD_OUT;
        this.rejectReason = "발급 수량이 모두 소진되었습니다.";
    }

    public void markRejected(String reason) {
        this.status = CouponIssueStatus.REJECTED;
        this.rejectReason = reason;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
