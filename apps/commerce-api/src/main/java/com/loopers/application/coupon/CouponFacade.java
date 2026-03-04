package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final IssuedCouponService issuedCouponService;

    // Command

    @Transactional
    public CouponInfo registerCoupon(CouponCommand.Register command) {
        Coupon coupon = couponService.register(command);
        return CouponInfo.from(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        couponService.delete(couponId);
        issuedCouponService.deleteAvailableByCouponId(couponId);
    }

    @Transactional
    public IssuedCouponInfo issueCoupon(Long couponId, Long userId) {
        Coupon coupon = couponService.issue(couponId);
        IssuedCoupon issuedCoupon = issuedCouponService.issue(couponId, userId);
        return IssuedCouponInfo.from(issuedCoupon, coupon);
    }

    @Transactional
    public CouponInfo updateCoupon(Long couponId, CouponCommand.Update command) {
        if (command.type() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 유형은 변경할 수 없습니다");
        }
        Coupon coupon = couponService.update(couponId, command);
        return CouponInfo.from(coupon);
    }

    // Query

    @Transactional(readOnly = true)
    public Page<CouponInfo> getCoupons(Pageable pageable) {
        Page<Coupon> coupons = couponService.findActiveCoupons(pageable);
        return coupons.map(CouponInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponInfo getCoupon(Long couponId) {
        Coupon coupon = couponService.getActiveCoupon(couponId);
        return CouponInfo.from(coupon);
    }
}
