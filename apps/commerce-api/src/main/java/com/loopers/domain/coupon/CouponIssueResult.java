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
 * 선착순 쿠폰 발급 요청의 처리 결과.
 * API에서 PENDING으로 생성 → Consumer가 ISSUED/SOLD_OUT/REJECTED로 갱신한다.
 * BaseEntity 미상속: soft delete 불필요, 발급 요청 추적이 목적.
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

    // 거절 사유 (SOLD_OUT, REJECTED 시)
    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public CouponIssueResult(Long couponTemplateId, Long userId) {
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = CouponIssueStatus.PENDING;
    }

    // Consumer에서 발급 성공 시 호출
    public void markIssued() {
        this.status = CouponIssueStatus.ISSUED;
    }

    // Consumer에서 매진 시 호출
    public void markSoldOut() {
        this.status = CouponIssueStatus.SOLD_OUT;
        this.rejectReason = "발급 수량이 모두 소진되었습니다.";
    }

    // Consumer에서 비즈니스 거절 시 호출
    public void markRejected(String reason) {
        this.status = CouponIssueStatus.REJECTED;
        this.rejectReason = reason;
    }

    @PrePersist
    private void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
