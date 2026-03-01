package com.loopers.interfaces.api.coupon.v1;

import java.time.ZonedDateTime;
import java.util.List;

import com.loopers.application.coupon.ReadMyOwnedCouponsUseCase;
import com.loopers.domain.coupon.CouponType;

public class OwnedCouponDto {

    public record OwnedCouponResponse(
            Long id,
            String status,
            ZonedDateTime createdAt,
            String name,
            CouponType couponType,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {

        public static OwnedCouponResponse from(ReadMyOwnedCouponsUseCase.Result result) {
            return new OwnedCouponResponse(
                    result.id(),
                    result.status().name(),
                    result.createdAt(),
                    result.name(),
                    result.couponType(),
                    result.discountValue(),
                    result.maxDiscountPrice(),
                    result.minOrderPrice(),
                    result.expiredAt()
            );
        }

        public static List<OwnedCouponResponse> from(List<ReadMyOwnedCouponsUseCase.Result> results) {
            return results.stream()
                    .map(OwnedCouponResponse::from)
                    .toList();
        }
    }
}