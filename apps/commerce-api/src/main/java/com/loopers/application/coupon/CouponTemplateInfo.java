package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateProjection;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 응답용 애플리케이션 DTO.
 */
public record CouponTemplateInfo(
    Long id,
    String name,
    String type,
    int value,
    BigDecimal minOrderAmount,
    ZonedDateTime expiredAt
) {
    public static CouponTemplateInfo from(CouponTemplateModel model) {
        if (model == null) {
            return null;
        }
        return new CouponTemplateInfo(
            model.getId(),
            model.getName(),
            model.getType().name(),
            model.getValue(),
            model.getMinOrderAmount(),
            model.getExpiredAt()
        );
    }

    /** 프로젝션 조회 결과를 Info로 변환. */
    public static CouponTemplateInfo from(CouponTemplateProjection projection) {
        if (projection == null) {
            return null;
        }
        return new CouponTemplateInfo(
            projection.getId(),
            projection.getName(),
            projection.getType().name(),
            projection.getValue(),
            projection.getMinOrderAmount(),
            projection.getExpiredAt()
        );
    }
}
