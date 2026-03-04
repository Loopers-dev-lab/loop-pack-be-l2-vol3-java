package com.loopers.application.coupon;

import com.loopers.application.user.UserService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final IssuedCouponService issuedCouponService;
    private final UserService userService;

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

    @Transactional(readOnly = true)
    public Page<IssuedCouponAdminInfo> getCouponIssues(Long couponId, Pageable pageable) {
        Coupon coupon = couponService.getActiveCoupon(couponId);
        Page<IssuedCoupon> issuedCoupons = issuedCouponService.findByCouponId(couponId, pageable);

        List<Long> userIds = issuedCoupons.getContent().stream()
                .map(IssuedCoupon::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = userService.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return issuedCoupons.map(ic -> IssuedCouponAdminInfo.from(ic, userMap.get(ic.getUserId()), coupon));
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getMyCoupons(Long userId, Pageable pageable) {
        Page<IssuedCoupon> issuedCoupons = issuedCouponService.findActiveByUserId(userId, pageable);

        List<Long> couponIds = issuedCoupons.getContent().stream()
                .map(IssuedCoupon::getCouponId)
                .distinct()
                .toList();
        Map<Long, Coupon> couponMap = couponService.findAllByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        return issuedCoupons.map(ic -> IssuedCouponInfo.from(ic, couponMap.get(ic.getCouponId())));
    }
}
