package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record UserCouponResponse(
        Long id,
        UserCouponStatus status,
        Long orderId,
        ZonedDateTime issuedAt,
        ZonedDateTime usedAt,
        CouponResponse coupon
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.status(),
                info.orderId(),
                info.issuedAt(),
                info.usedAt(),
                CouponResponse.from(info.coupon())
            );
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt()
            );
        }
    }
}
