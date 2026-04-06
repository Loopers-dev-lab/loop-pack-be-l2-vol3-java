package com.loopers.infrastructure.coupon.repository.impl;

import com.loopers.domain.coupon.model.FirstComeCoupon;
import com.loopers.domain.coupon.repository.FirstComeCouponRepository;
import com.loopers.infrastructure.coupon.entity.FirstComeCouponEntity;
import com.loopers.infrastructure.coupon.repository.FirstComeCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class FirstComeCouponRepositoryImpl implements FirstComeCouponRepository {

    private final FirstComeCouponJpaRepository jpaRepository;

    @Override
    public Optional<FirstComeCoupon> findByTemplateId(Long couponTemplateId) {
        return jpaRepository.findByCouponTemplateId(couponTemplateId).map(FirstComeCouponEntity::toModel);
    }
}
