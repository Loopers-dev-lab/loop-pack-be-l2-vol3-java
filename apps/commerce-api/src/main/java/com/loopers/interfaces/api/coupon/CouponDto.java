package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.util.List;

public class CouponDto {

    public static record IssueResponse(Long issuedCouponId) {

    }

    public static record IssueAsyncResponse(String requestId) {

    }

    public static record IssueRequestStatusResponse(String requestId, CouponIssueStatus status, String failReason) {

        public static IssueRequestStatusResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestStatusResponse(info.requestId(), info.status(), info.failReason());
        }
    }

    public static record MyCouponList(List<MyCoupon> myCoupons) {

        public static MyCouponList from(List<MyCouponInfo> myCouponInfos) {
            return new MyCouponList(myCouponInfos.stream()
                            .map(MyCoupon::from)
                            .toList());
        }
    }

    public static record MyCoupon(Long couponId, String name, String type, int value, String status) {

        public static MyCoupon from(MyCouponInfo myCouponInfo) {
            return new MyCoupon(myCouponInfo.id(), myCouponInfo.name(), myCouponInfo. type(), myCouponInfo.value(), myCouponInfo.status());
        }
    }

}
