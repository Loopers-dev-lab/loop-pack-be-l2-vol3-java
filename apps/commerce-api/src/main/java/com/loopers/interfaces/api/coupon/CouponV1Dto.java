package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Dto {

    public record IssuedCouponResponse(
        Long issuedCouponId,
        Long couponTemplateId,
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        CouponStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static IssuedCouponResponse from(CouponInfo.IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                info.issuedCouponId(),
                info.couponTemplateId(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.status(),
                info.expiredAt(),
                info.usedAt()
            );
        }
    }

    public record MyCouponsResponse(List<IssuedCouponResponse> coupons) {
        public static MyCouponsResponse from(List<CouponInfo.IssuedCouponInfo> infos) {
            return new MyCouponsResponse(
                infos.stream().map(IssuedCouponResponse::from).toList()
            );
        }
    }
}
