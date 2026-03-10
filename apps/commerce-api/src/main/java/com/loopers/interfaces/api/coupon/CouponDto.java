package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponDto {

    public record IssuedCouponResponse(
            Long issuedCouponId,
            Long couponId,
            String couponName,
            IssuedCouponStatus status,
            ZonedDateTime issuedAt,
            ZonedDateTime expiredAt,
            ZonedDateTime usedAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                    info.getIssuedCouponId(),
                    info.getCouponId(),
                    info.getCouponName(),
                    info.getStatus(),
                    info.getIssuedAt(),
                    info.getExpiredAt(),
                    info.getUsedAt()
            );
        }
    }

    public record IssuedCouponListResponse(List<IssuedCouponResponse> coupons) {
        public static IssuedCouponListResponse from(List<IssuedCouponInfo> infoList) {
            return new IssuedCouponListResponse(infoList.stream().map(IssuedCouponResponse::from).toList());
        }
    }
}
