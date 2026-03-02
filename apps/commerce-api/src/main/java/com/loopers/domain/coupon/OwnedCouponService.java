package com.loopers.domain.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.coupon.discount.CouponDiscount;
import com.loopers.domain.coupon.discount.CouponDiscountProvider;
import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public class OwnedCouponService {

    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;
    private final CouponDiscountProvider couponDiscountProvider;

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

    @Transactional
    public CouponDiscount applyCoupon(Long ownedCouponId, Long userId, Money orderTotal) {
        OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.OWNED_COUPON_NOT_FOUND));

        ownedCoupon.validateOwner(userId);
        Coupon coupon = ownedCoupon.getCoupon();
        coupon.validateMinOrderPrice(orderTotal);
        ownedCoupon.use();

        Money discountAmount = coupon.calculateDiscount(orderTotal, couponDiscountProvider);
        return new CouponDiscount(discountAmount, ownedCouponId);
    }
}
