package com.loopers.application.fcfs;

import com.loopers.domain.fcfs.CouponIssueRequestStatus;

/**
 * 쿠폰 발급 요청 결과 DTO.
 */
public record CouponIssueRequestResult(
    String requestId,
    CouponIssueRequestStatus status
) {
    public static CouponIssueRequestResult of(String requestId, CouponIssueRequestStatus status) {
        return new CouponIssueRequestResult(requestId, status);
    }
}
