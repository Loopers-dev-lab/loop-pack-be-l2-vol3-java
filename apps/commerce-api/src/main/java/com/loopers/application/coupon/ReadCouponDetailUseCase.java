package com.loopers.application.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ReadCouponDetailUseCase {

    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public CouponResult execute(Long couponId) {
        return couponRepository.findById(couponId)
                .map(CouponResult::from)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
    }
}
