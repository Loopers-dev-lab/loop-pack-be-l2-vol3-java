package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CouponFacade {
    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponAppService couponAppService;

    @Transactional
    public IssuedCoupon issueCoupon(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        coupon.validateIssuable();
        coupon.issue();

        IssuedCoupon issuedCoupon = IssuedCoupon.create(coupon, userId);
        return issuedCouponRepository.save(issuedCoupon);
    }

    @Transactional(readOnly = true)
    public List<IssuedCouponInfo> getMyIssuedCoupons(Long userId) {
        List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findByUserId(userId);
        if (issuedCoupons.isEmpty()) {
            return List.of();
        }

        List<Long> couponIds = issuedCoupons.stream().map(IssuedCoupon::getCouponId).distinct().toList();
        Map<Long, Coupon> couponMap = couponRepository.findByIdIn(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        return issuedCoupons.stream()
                .map(ic -> {
                    Coupon coupon = couponMap.get(ic.getCouponId());
                    String couponName = coupon != null ? coupon.getName() : "";
                    return IssuedCouponInfo.of(ic, couponName);
                })
                .toList();
    }
}
