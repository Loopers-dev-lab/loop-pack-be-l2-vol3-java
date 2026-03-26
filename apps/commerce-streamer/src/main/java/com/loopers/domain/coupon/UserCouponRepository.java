package com.loopers.domain.coupon;

/**
 * 사용자 발급 쿠폰 레포지토리 인터페이스 (Streamer 도메인 레이어).
 */
public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel userCoupon);
}
