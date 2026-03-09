package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserCouponService userCouponService;
    private final UserService userService;

    public CouponInfo register(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponModel coupon = couponService.register(name, type, value, minOrderAmount, expiredAt);
        return CouponInfo.from(coupon);
    }

    public CouponInfo update(Long couponId, String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponModel coupon = couponService.update(couponId, name, type, value, minOrderAmount, expiredAt);
        return CouponInfo.from(coupon);
    }

    public void delete(Long couponId) {
        couponService.delete(couponId);
    }

    public CouponInfo getCoupon(Long couponId) {
        CouponModel coupon = couponService.getCoupon(couponId);
        return CouponInfo.from(coupon);
    }

    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponService.getAll(pageable).map(CouponInfo::from);
    }

    public UserCouponInfo issue(String loginId, String password, Long couponId) {
        UserModel user = userService.getMyInfo(loginId, password);
        UserCouponModel userCoupon = userCouponService.issue(user.getId(), couponId);
        return UserCouponInfo.from(userCoupon);
    }

    public List<UserCouponInfo> getMyCoupons(String loginId, String password) {
        UserModel user = userService.getMyInfo(loginId, password);
        return userCouponService.getMyCoupons(user.getId()).stream()
            .map(UserCouponInfo::from)
            .toList();
    }

    public Page<UserCouponInfo> getCouponIssues(Long couponId, Pageable pageable) {
        return userCouponService.getCouponIssues(couponId, pageable).map(UserCouponInfo::from);
    }
}
