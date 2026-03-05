package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class IssuedCouponInfo {
    private final Long issuedCouponId;
    private final Long couponId;
    private final Long userId;
    private final IssuedCouponStatus status;
    private final Long usedOrderId;
    private final ZonedDateTime issuedAt;
    private final ZonedDateTime expiredAt;
    private final ZonedDateTime usedAt;
    private final String couponName;

    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon) {
        return IssuedCouponInfo.builder()
                .issuedCouponId(issuedCoupon.getId())
                .couponId(issuedCoupon.getCouponId())
                .userId(issuedCoupon.getUserId())
                .status(issuedCoupon.getStatus())
                .usedOrderId(issuedCoupon.getUsedOrderId())
                .issuedAt(issuedCoupon.getIssuedAt())
                .expiredAt(issuedCoupon.getExpiredAt())
                .usedAt(issuedCoupon.getUsedAt())
                .build();
    }

    public static IssuedCouponInfo of(IssuedCoupon issuedCoupon, String couponName) {
        return IssuedCouponInfo.builder()
                .issuedCouponId(issuedCoupon.getId())
                .couponId(issuedCoupon.getCouponId())
                .userId(issuedCoupon.getUserId())
                .status(issuedCoupon.getStatus())
                .usedOrderId(issuedCoupon.getUsedOrderId())
                .issuedAt(issuedCoupon.getIssuedAt())
                .expiredAt(issuedCoupon.getExpiredAt())
                .usedAt(issuedCoupon.getUsedAt())
                .couponName(couponName)
                .build();
    }
}
