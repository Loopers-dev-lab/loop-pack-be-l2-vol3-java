package com.loopers.application.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class CouponInfo {
    private final Long couponId;
    private final String name;
    private final DiscountType discountType;
    private final Money discountValue;
    private final Money minOrderAmount;
    private final Money maxDiscountAmount;
    private final int totalQuantity;
    private final int issuedQuantity;
    private final ZonedDateTime validFrom;
    private final ZonedDateTime validUntil;
    private final boolean deleted;

    public static CouponInfo from(Coupon coupon) {
        return CouponInfo.builder()
                .couponId(coupon.getId())
                .name(coupon.getName())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .issuedQuantity(coupon.getIssuedQuantity())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .deleted(coupon.isDeleted())
                .build();
    }
}
