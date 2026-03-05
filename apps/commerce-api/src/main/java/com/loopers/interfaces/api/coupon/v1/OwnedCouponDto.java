package com.loopers.interfaces.api.coupon.v1;

import java.time.ZonedDateTime;
import java.util.List;

import com.loopers.application.coupon.ReadMyOwnedCouponsUseCase;
import com.loopers.application.coupon.ReadOwnedCouponsUseCase;
import com.loopers.domain.coupon.CouponType;

public class OwnedCouponDto {

    public record MyOwnedCouponsResponse(
            Long id,
            String status,
            ZonedDateTime createdAt,
            String name,
            CouponType couponType,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
    ) {

        public static MyOwnedCouponsResponse from(ReadMyOwnedCouponsUseCase.Result result) {
            return new MyOwnedCouponsResponse(
                    result.id(),
                    result.status(),
                    result.createdAt(),
                    result.name(),
                    result.couponType(),
                    result.discountValue(),
                    result.maxDiscountPrice(),
                    result.minOrderPrice(),
                    result.expiredAt(),
                    result.usedAt()
            );
        }

        public static List<MyOwnedCouponsResponse> from(List<ReadMyOwnedCouponsUseCase.Result> results) {
            return results.stream()
                    .map(MyOwnedCouponsResponse::from)
                    .toList();
        }
    }

    public record OwnedCouponsResponse(
            Long id,
            String status,
            ZonedDateTime createdAt,
            String name,
            CouponType couponType,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt,
            UserInfo user
    ) {

        public record UserInfo(
                Long userId,
                String loginId,
                String userName
        ) {
        }

        public static OwnedCouponsResponse from(ReadOwnedCouponsUseCase.Result result) {
            return new OwnedCouponsResponse(
                    result.id(),
                    result.status(),
                    result.createdAt(),
                    result.name(),
                    result.couponType(),
                    result.discountValue(),
                    result.maxDiscountPrice(),
                    result.minOrderPrice(),
                    result.expiredAt(),
                    result.usedAt(),
                    new UserInfo(
                            result.userId(),
                            result.loginId(),
                            result.userName()
                    )
            );
        }

        public static List<OwnedCouponsResponse> from(List<ReadOwnedCouponsUseCase.Result> results) {
            return results.stream()
                    .map(OwnedCouponsResponse::from)
                    .toList();
        }
    }
}
