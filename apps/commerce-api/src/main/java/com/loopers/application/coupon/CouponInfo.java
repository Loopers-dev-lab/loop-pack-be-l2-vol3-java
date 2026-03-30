package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public record CouponInfo(
    Long id,
    String name,
    CouponType type,
    Long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    Long issueLimit,
    Long issuedCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static CouponInfo from(CouponModel model) {
        return new CouponInfo(
            model.getId(),
            model.getName(),
            model.getType(),
            model.getValue(),
            model.getMinOrderAmount(),
            model.getExpiredAt(),
            model.getIssueLimit(),
            model.getIssuedCount(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
