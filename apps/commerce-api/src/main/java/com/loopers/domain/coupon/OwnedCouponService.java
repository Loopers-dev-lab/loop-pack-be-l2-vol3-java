package com.loopers.domain.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public class OwnedCouponService {

    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;

    @Transactional
    public OwnedCoupon issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findByIdAndDeletedAtIsNull(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        if (ownedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw new CoreException(ErrorType.ALREADY_COUPON_ISSUED);
        }
        OwnedCoupon ownedCoupon = OwnedCoupon.create(coupon, userId);
        return ownedCouponRepository.save(ownedCoupon);
    }
}
