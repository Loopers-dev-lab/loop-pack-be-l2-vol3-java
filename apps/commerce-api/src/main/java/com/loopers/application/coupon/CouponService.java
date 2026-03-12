package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponModel create(String name, CouponType type, int value, Money minOrderAmount, LocalDateTime expiredAt) {
        CouponModel coupon = new CouponModel(name, type, value, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public CouponModel getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponModel> getAllCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional
    public CouponModel update(Long couponId, String name, CouponType type, int value, Money minOrderAmount, LocalDateTime expiredAt) {
        CouponModel coupon = getCoupon(couponId);
        coupon.update(name, type, value, minOrderAmount, expiredAt);
        return coupon;
    }

    @Transactional
    public void delete(Long couponId) {
        CouponModel coupon = getCoupon(couponId);
        coupon.delete();
    }
}
