package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    long countByCouponTemplateId(Long couponTemplateId);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
}
