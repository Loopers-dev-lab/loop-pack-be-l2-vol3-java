package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 발급 쿠폰 JPA 레포지토리 (Streamer 모듈).
 */
public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {
}
