package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.IssuedCouponInfo;

public class CouponV1Dto {

    public record IssuedCouponResponse(Long couponId, String status) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(info.couponId(), info.status());
        }
    }

    public record IssueRequestResponse(Long requestId, Long couponId, String status) {
        public static IssueRequestResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestResponse(info.requestId(), info.couponId(), info.status());
        }
    }
}
