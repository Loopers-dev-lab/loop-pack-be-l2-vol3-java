package com.loopers.infrastructure.coupon.persistence;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long couponId) {
        return couponJpaRepository.findById(couponId);
    }

    @Override
    public Slice<Coupon> findAllBy(Pageable pageable) {
        return couponJpaRepository.findAllBy(pageable);
    }
}
