package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;

    @Override
    public void save(UserCoupon userCoupon) {
        jpaRepository.save(userCoupon);
    }

    @Override
    public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
        return jpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId);
    }
}
