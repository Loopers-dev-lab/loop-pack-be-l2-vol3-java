package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.Money;

import java.time.LocalDateTime;

public class CouponAdminV1Dto {

    public record CreateRequest(
        String name,
        CouponType type,
        int value,
        Integer minOrderAmount,
        LocalDateTime expiredAt
    ) {
        public Money toMinOrderAmount() {
            return minOrderAmount != null ? new Money(minOrderAmount) : null;
        }
    }

    public record UpdateRequest(
        String name,
        CouponType type,
        int value,
        Integer minOrderAmount,
        LocalDateTime expiredAt
    ) {
        public Money toMinOrderAmount() {
            return minOrderAmount != null ? new Money(minOrderAmount) : null;
        }
    }

    public record CouponResponse(
        Long id,
        String name,
        String type,
        int value,
        Integer minOrderAmount,
        LocalDateTime expiredAt
    ) {
        public static CouponResponse from(CouponModel coupon) {
            return new CouponResponse(
                coupon.getId(),
                coupon.name(),
                coupon.type().name(),
                coupon.value(),
                coupon.minOrderAmount() != null ? coupon.minOrderAmount().value() : null,
                coupon.expiredAt()
            );
        }
    }
}
