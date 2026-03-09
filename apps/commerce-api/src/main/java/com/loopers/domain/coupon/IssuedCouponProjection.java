package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

/**
 * 발급 쿠폰 조회 전용 프로젝션(읽기 전용).
 * DTO Projection으로 엔티티 대신 필요한 필드만 조회. (05-transaction-query §8.2)
 */
public interface IssuedCouponProjection {

    Long getId();

    Long getCouponId();

    IssuedCouponStatus getStatus();

    ZonedDateTime getExpiredAt();

    ZonedDateTime getUsedAt();

    ZonedDateTime getCreatedAt();
}
