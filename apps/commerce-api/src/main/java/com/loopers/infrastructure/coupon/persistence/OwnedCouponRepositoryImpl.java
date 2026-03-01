package com.loopers.infrastructure.coupon.persistence;

import org.springframework.stereotype.Repository;

import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OwnedCouponRepositoryImpl implements OwnedCouponRepository {

    private final OwnedCouponJpaRepository ownedCouponJpaRepository;

    @Override
    public OwnedCoupon save(OwnedCoupon ownedCoupon) {
        return ownedCouponJpaRepository.save(ownedCoupon);
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return ownedCouponJpaRepository.existsByCouponIdAndUserId(couponId, userId);
    }
}
