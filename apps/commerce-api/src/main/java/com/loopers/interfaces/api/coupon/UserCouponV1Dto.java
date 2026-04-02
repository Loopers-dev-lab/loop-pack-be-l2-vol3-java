package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponUsageType;
import com.loopers.domain.coupon.DiscountType;
import java.time.ZonedDateTime;
import java.util.List;

public class UserCouponV1Dto {

    public record CouponResponse(
        Long id,
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        CouponUsageType usageType
    ) {
        public static CouponResponse from(CouponInfo couponInfo) {
            return new CouponResponse(
                couponInfo.id(),
                couponInfo.name(),
                couponInfo.discountType(),
                couponInfo.discountValue(),
                couponInfo.minOrderAmount(),
                couponInfo.expiredAt(),
                couponInfo.usageType()
            );
        }
    }

    public record MyCouponListResponse(
        List<CouponResponse> coupons
    ) {
        public static MyCouponListResponse from(List<CouponInfo> couponInfos) {
            List<CouponResponse> responses = couponInfos.stream()
                .map(CouponResponse::from)
                .toList();
            return new MyCouponListResponse(responses);
        }
    }

    public record CouponIssueRequestResponse(
        Long requestId,
        Long templateId,
        CouponIssueRequestStatus status,
        String failReason
    ) {
        public static CouponIssueRequestResponse from(CouponIssueRequestInfo info) {
            return new CouponIssueRequestResponse(
                info.requestId(),
                info.templateId(),
                info.status(),
                info.failReason()
            );
        }
    }
}
