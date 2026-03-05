package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }

    @Override
    public Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId) {
        return issuedCouponJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public List<IssuedCoupon> findByUserId(Long userId) {
        return issuedCouponJpaRepository.findByUserId(userId);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return issuedCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public int useById(Long id, Long userId) {
        return issuedCouponJpaRepository.useById(id, userId, LocalDateTime.now());
    }

    @Override
    public Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponJpaRepository.findByCouponId(couponId, pageable);
    }
}
