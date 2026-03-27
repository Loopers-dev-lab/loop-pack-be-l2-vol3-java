package com.loopers.domain.coupon.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CouponIssueRequest {

    private Long id;
    private Long couponTemplateId;
    private Long memberId;
    private CouponIssueStatus status;
    private LocalDateTime createdAt;

    private CouponIssueRequest(Long couponTemplateId, Long memberId) {
        this.couponTemplateId = couponTemplateId;
        this.memberId = memberId;
        this.status = CouponIssueStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static CouponIssueRequest create(Long couponTemplateId, Long memberId) {
        return new CouponIssueRequest(couponTemplateId, memberId);
    }

    public static CouponIssueRequest reconstruct(Long id, Long couponTemplateId, Long memberId,
                                                   CouponIssueStatus status, LocalDateTime createdAt) {
        CouponIssueRequest request = new CouponIssueRequest(couponTemplateId, memberId);
        request.id = id;
        request.status = status;
        request.createdAt = createdAt;
        return request;
    }

    public void markIssued() {
        this.status = CouponIssueStatus.ISSUED;
    }

    public void markRejected() {
        this.status = CouponIssueStatus.REJECTED;
    }
}
