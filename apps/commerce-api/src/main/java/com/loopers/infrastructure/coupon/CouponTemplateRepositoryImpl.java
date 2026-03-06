package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        return couponTemplateJpaRepository.save(couponTemplate);
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }
}
