package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;

    @Override
    public boolean existsByCouponIdAndMemberId(Long couponId, Long memberId) {
        return jpaRepository.existsByCouponIdAndMemberId(couponId, memberId);
    }

    @Override
    public UserCouponModel save(UserCouponModel model) {
        return jpaRepository.save(model);
    }
}
