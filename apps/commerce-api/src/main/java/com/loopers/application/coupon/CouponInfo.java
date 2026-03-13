package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.ZonedDateTime;

public class CouponInfo {

    public record IssuedCouponInfo(
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
        public static IssuedCouponInfo of(IssuedCoupon issued, CouponTemplate template) {
            CouponStatus effectiveStatus = issued.getStatus();
            if (effectiveStatus == CouponStatus.AVAILABLE && template.isExpired()) {
                effectiveStatus = CouponStatus.EXPIRED;
            }
            return new IssuedCouponInfo(
                issued.getId(),
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                effectiveStatus,
                template.getExpiredAt(),
                issued.getUsedAt()
            );
        }
    }

    public record CouponTemplateInfo(
        Long id,
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt
    ) {
        public static CouponTemplateInfo from(CouponTemplate template) {
            return new CouponTemplateInfo(
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getExpiredAt(),
                template.getCreatedAt()
            );
        }
    }

    public record IssuedCouponDetailInfo(
        Long issuedCouponId,
        Long memberId,
        CouponStatus status,
        ZonedDateTime usedAt,
        ZonedDateTime createdAt
    ) {
        public static IssuedCouponDetailInfo from(IssuedCoupon issued) {
            return new IssuedCouponDetailInfo(
                issued.getId(),
                issued.getMemberId(),
                issued.getStatus(),
                issued.getUsedAt(),
                issued.getCreatedAt()
            );
        }
    }
}
