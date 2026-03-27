package com.loopers.domain.coupon;

import java.util.Optional;

/**
 * 쿠폰 도메인 리포지토리 인터페이스.
 */
public interface CouponRepository {

    /**
     * ID로 쿠폰을 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰 (존재하지 않으면 빈 Optional)
     */
    Optional<Coupon> findById(Long couponId);
}
