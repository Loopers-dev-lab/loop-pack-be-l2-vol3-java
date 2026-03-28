package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CouponDomainService {

    private final CouponRepository couponRepository;

    public Coupon getById(Long id) {
        return couponRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }
}
