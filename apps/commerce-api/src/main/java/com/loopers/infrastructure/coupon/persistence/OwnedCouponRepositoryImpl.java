package com.loopers.infrastructure.coupon.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
    public Slice<OwnedCoupon> findAllByCouponId(Long couponId, Pageable pageable) {
        return ownedCouponJpaRepository.findAllByCouponId(couponId, pageable);
    }

    @Override
    public Slice<OwnedCoupon> findAllByUserId(Long userId, Pageable pageable) {
        return ownedCouponJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return ownedCouponJpaRepository.existsByCouponIdAndUserId(couponId, userId);
    }
}
