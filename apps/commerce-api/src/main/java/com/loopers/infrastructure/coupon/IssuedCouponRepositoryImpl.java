package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    // Command

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }

    @Override
    public int markUsedIfAvailable(Long id, Long userId) {
        return issuedCouponJpaRepository.markUsedIfAvailable(id, userId);
    }

    // Query

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public Page<IssuedCoupon> findAllByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponJpaRepository.findAllByCouponId(couponId, pageable);
    }

    @Override
    public Page<IssuedCoupon> findActiveByUserId(Long userId, Pageable pageable) {
        return issuedCouponJpaRepository.findActiveByUserId(userId, pageable);
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return issuedCouponJpaRepository.existsByCouponIdAndUserId(couponId, userId);
    }
}
