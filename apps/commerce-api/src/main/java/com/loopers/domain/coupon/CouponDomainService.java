package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
public class CouponDomainService {

    private final CouponRepository couponRepository;

    public Coupon register(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        return couponRepository.save(new Coupon(name, type, value, minOrderAmount, expiredAt));
    }

    public Coupon getById(Long id) {
        return couponRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    public PageResult<Coupon> getAll(int page, int size) {
        return couponRepository.findAll(page, size);
    }

    public Coupon update(Long id, String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        Coupon coupon = getById(id);
        coupon.changeDetails(name, type, value, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    public void delete(Long id) {
        Coupon coupon = getById(id);
        coupon.delete();
        couponRepository.save(coupon);
    }
}
