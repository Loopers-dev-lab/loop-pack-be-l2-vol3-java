package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponAdminV1Dto {

    // Response

    public record IssuedCouponResponse(
            Long id,
            Long userId,
            String loginId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime usedAt
    ) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(
                    info.id(),
                    info.userId(),
                    info.loginId(),
                    info.status().name(),
                    info.createdAt(),
                    info.usedAt()
            );
        }
    }

    public record CouponResponse(
            Long id,
            String name,
            String type,
            int value,
            BigDecimal minOrderAmount,
            int maxIssueCount,
            int issuedCount,
            LocalDateTime expiredAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static CouponResponse from(CouponInfo info) {
            return new CouponResponse(
                    info.id(),
                    info.name(),
                    info.type().name(),
                    info.value(),
                    info.minOrderAmount(),
                    info.maxIssueCount(),
                    info.issuedCount(),
                    info.expiredAt(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
