package com.loopers.domain.coupon;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 조회 전용 프로젝션(읽기 전용).
 * DTO Projection으로 엔티티 대신 필요한 필드만 조회. (05-transaction-query §8.2)
 */
public interface CouponTemplateProjection {

    Long getId();

    String getName();

    CouponType getType();

    int getValue();

    BigDecimal getMinOrderAmount();

    ZonedDateTime getExpiredAt();
}
