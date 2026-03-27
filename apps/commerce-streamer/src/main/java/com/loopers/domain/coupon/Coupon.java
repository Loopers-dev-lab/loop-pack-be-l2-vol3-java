package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * commerce-streamer용 경량 Coupon Entity.
 * coupons 테이블의 발급 관련 필드만 매핑.
 * 읽기 + Atomic UPDATE(issueIfAvailable) 용도.
 */
@Entity
@Table(name = "coupons")
@Getter
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_issue_count", nullable = false)
    private int maxIssueCount;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Coupon() {
    }
}
