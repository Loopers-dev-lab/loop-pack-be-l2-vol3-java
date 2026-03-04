package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponAdminV1Dto {

    // Response

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
