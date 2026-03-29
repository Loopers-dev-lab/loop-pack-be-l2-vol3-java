package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public Coupon createCoupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        return couponRepository.save(new Coupon(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional
    public Coupon createCoupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuable) {
        return couponRepository.save(new Coupon(name, type, value, minOrderAmount, expiredAt, maxIssuable));
    }

    /**
     * name, minOrderAmount, expiredAt만 수정 가능.
     * requestedType 또는 requestedValue가 non-null이면 불변 위반으로 에러.
     */
    @Transactional
    public Coupon updateCoupon(Long couponId, String name, CouponType requestedType, Integer requestedValue, int minOrderAmount, ZonedDateTime expiredAt) {
        Coupon coupon = getActiveCoupon(couponId);
        if (requestedType != null || requestedValue != null) {
            throw new CoreException(ErrorType.COUPON_TYPE_IMMUTABLE);
        }
        coupon.update(name, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = getActiveCoupon(couponId);
        coupon.delete();
        couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long couponId) {
        return getActiveCoupon(couponId);
    }

    @Transactional(readOnly = true)
    public Page<Coupon> getCoupons(Pageable pageable) {
        return couponRepository.findAllNotDeleted(pageable);
    }

    @Transactional(readOnly = true)
    public Coupon getCouponForIssue(Long couponId) {
        return getActiveCoupon(couponId);
    }

    private Coupon getActiveCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        if (coupon.getDeletedAt() != null) {
            throw new CoreException(ErrorType.COUPON_NOT_FOUND);
        }
        return coupon;
    }
}
