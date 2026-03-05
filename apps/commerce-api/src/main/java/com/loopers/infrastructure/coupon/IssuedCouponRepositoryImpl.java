package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<IssuedCoupon> findByIdWithLock(Long id) {
        return issuedCouponJpaRepository.findByIdWithLock(id);
    }

    @Override
    public Optional<IssuedCoupon> findByCouponIdAndUserId(Long couponId, Long userId) {
        return issuedCouponJpaRepository.findByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public Optional<IssuedCoupon> findByCouponIdAndUserIdWithLock(Long couponId, Long userId) {
        return issuedCouponJpaRepository.findByCouponIdAndUserIdWithLock(couponId, userId);
    }

    @Override
    public List<IssuedCoupon> findByUserId(Long userId) {
        return issuedCouponJpaRepository.findByUserId(userId);
    }

    @Override
    public List<IssuedCoupon> findByCouponId(Long couponId) {
        return issuedCouponJpaRepository.findByCouponId(couponId);
    }

    @Override
    public Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponJpaRepository.findByCouponId(couponId, pageable);
    }
}
