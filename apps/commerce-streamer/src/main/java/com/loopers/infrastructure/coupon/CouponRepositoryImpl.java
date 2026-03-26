package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 쿠폰 레포지토리 구현체 (Streamer 모듈).
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public int incrementIssuedCountWithCas(Long couponId) {
        return couponJpaRepository.incrementIssuedCountWithCas(couponId);
    }

    @Override
    public Optional<CouponModel> findById(Long couponId) {
        return couponJpaRepository.findById(couponId);
    }
}
