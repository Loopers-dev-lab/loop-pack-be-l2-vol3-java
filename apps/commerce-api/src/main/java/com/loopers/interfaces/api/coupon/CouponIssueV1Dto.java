package com.loopers.interfaces.api.coupon;

public class CouponIssueV1Dto {

    public record IssueAsyncResponse(
            String requestId,
            String status
    ) {
    }
}
