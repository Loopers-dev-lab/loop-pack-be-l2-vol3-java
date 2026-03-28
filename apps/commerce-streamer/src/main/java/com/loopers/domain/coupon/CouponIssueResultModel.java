package com.loopers.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "coupon_issue_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueResultModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String requestId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CouponIssueStatus status;

    @Column(length = 200)
    private String failReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    public void markSuccess() {
        this.status = CouponIssueStatus.SUCCESS;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = CouponIssueStatus.FAILED;
        this.failReason = reason;
        this.processedAt = LocalDateTime.now();
    }
}
