package com.loopers.application.fcfs;

import com.loopers.domain.fcfs.CouponIssueRequestStatus;

/**
 * 쿠폰 발급 결과 조회 응답 DTO.
 */
public record CouponIssueResultResponse(
    String requestId,
    CouponIssueRequestStatus status,
    String failureReason
) {
    public static CouponIssueResultResponse of(String requestId, CouponIssueRequestStatus status, String failureReason) {
        return new CouponIssueResultResponse(requestId, status, failureReason);
    }
}
