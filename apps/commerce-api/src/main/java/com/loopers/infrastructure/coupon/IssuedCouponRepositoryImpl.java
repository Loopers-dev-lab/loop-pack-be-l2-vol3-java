package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    public IssuedCouponRepositoryImpl(IssuedCouponJpaRepository issuedCouponJpaRepository) {
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
    }

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        return issuedCouponJpaRepository.save(issuedCoupon);
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return issuedCouponJpaRepository.findById(id);
    }

    @Override
    public long countByCouponTemplateId(Long couponTemplateId) {
        return issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
    }

    @Override
    public long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId) {
        return issuedCouponJpaRepository.countByCouponTemplateIdAndUserId(couponTemplateId, userId);
    }

    @Override
    public List<IssuedCoupon> findAllByUserId(Long userId) {
        return issuedCouponJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
}
