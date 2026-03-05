package com.loopers.application.coupon;

import com.loopers.domain.coupon.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    @Transactional
    public CouponInfo createCoupon(CreateCouponCommand command) {
        Coupon coupon = couponService.createCoupon(command);
        return CouponInfo.from(coupon);
    }

    public CouponInfo getCoupon(Long couponId) {
        Coupon coupon = couponService.getById(couponId);
        return CouponInfo.from(coupon);
    }

    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponService.getAll(pageable).map(CouponInfo::from);
    }

    @Transactional
    public CouponInfo updateCoupon(Long couponId, UpdateCouponCommand command) {
        Coupon coupon = couponService.updateCoupon(couponId, command);
        return CouponInfo.from(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }

    @Transactional
    public UserCouponInfo issueCoupon(Long userId, Long couponId) {
        UserCoupon userCoupon = couponService.issueCoupon(userId, couponId);
        return UserCouponInfo.from(userCoupon);
    }

    public List<UserCouponInfo> getUserCoupons(Long userId) {
        List<UserCoupon> userCoupons = couponService.getUserCoupons(userId);
        return userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponService.getById(uc.getCouponId());
                    return UserCouponInfo.from(uc, coupon);
                })
                .toList();
    }

    public Page<UserCouponInfo> getIssuedCoupons(Long couponId, Pageable pageable) {
        return couponService.getIssuedCoupons(couponId, pageable)
                .map(UserCouponInfo::from);
    }
}
