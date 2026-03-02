package com.loopers.application.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 쿠폰 상세 정보를 조회합니다.
 */
@UseCase
@RequiredArgsConstructor
public class ReadCouponDetailUseCase {

    private final CouponRepository couponRepository;

    /**
     * @param couponId 조회할 쿠폰 ID
     * @return 쿠폰 상세 정보
     */
    @Transactional(readOnly = true)
    public CouponResult execute(Long couponId) {
        return couponRepository.findById(couponId)
                .map(CouponResult::from)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
    }
}
