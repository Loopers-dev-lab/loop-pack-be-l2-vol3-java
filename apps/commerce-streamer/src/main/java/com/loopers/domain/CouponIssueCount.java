package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 쿠폰 발급 수량 추적 테이블
 *
 * AtomicLong(메모리) 대신 DB로 관리 → streamer 재시작 후에도 수량 유지
 * Consumer가 단일 파티션에서 순차 처리하므로 별도 락 없이 안전
 */
@Entity
@Table(name = "coupon_issue_count")
public class CouponIssueCount {

    @Id
    @Column(name = "coupon_template_id")
    private Long couponTemplateId;

    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    protected CouponIssueCount() {}

    public static CouponIssueCount init(Long couponTemplateId) {
        CouponIssueCount c = new CouponIssueCount();
        c.couponTemplateId = couponTemplateId;
        c.issuedCount = 0;
        return c;
    }

    public boolean tryIncrement(long maxCount) {
        if (issuedCount >= maxCount) return false;
        issuedCount++;
        return true;
    }

    public long getIssuedCount() { return issuedCount; }
}
