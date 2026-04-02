package com.loopers.interfaces.api.fcfs;

import com.loopers.domain.fcfs.CouponIssueRequestStatus;

/**
 * 선착순 쿠폰 API DTO.
 */
public class FcfsCouponV1Dto {

    private FcfsCouponV1Dto() {}

    public record IssueRequest(
        Long userId
    ) {}

    public record IssueResponse(
        String requestId,
        CouponIssueRequestStatus status
    ) {}

    public record IssueResultResponse(
        String requestId,
        CouponIssueRequestStatus status,
        String failureReason
    ) {}
}
