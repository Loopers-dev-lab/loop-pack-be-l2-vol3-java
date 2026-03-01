package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;

import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public Coupon create(
            String name,
            CouponType type,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {
        Coupon coupon = Coupon.create(name, type, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        return couponRepository.save(coupon);
    }
}
