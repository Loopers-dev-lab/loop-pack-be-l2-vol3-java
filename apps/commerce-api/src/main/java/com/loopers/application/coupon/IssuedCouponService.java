package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    // Command

    @Transactional
    public IssuedCoupon issue(Long couponId, Long userId, String couponName,
                               CouponType couponType, int couponValue,
                               BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        if (issuedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다");
        }
        IssuedCoupon issuedCoupon = IssuedCoupon.create(couponId, userId, couponName,
                couponType, couponValue, minOrderAmount, expiredAt);
        return issuedCouponRepository.save(issuedCoupon);
    }

    @Transactional
    public void deleteAvailableByCouponId(Long couponId) {
        issuedCouponRepository.deleteAvailableByCouponId(couponId);
    }

    @Transactional
    public void markUsed(Long issuedCouponId, Long userId) {
        int updated = issuedCouponRepository.markUsed(issuedCouponId, userId);
        if (updated == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다");
        }
    }

    // Query

    @Transactional(readOnly = true)
    public IssuedCoupon getUsableCoupon(Long couponId, Long userId) {
        IssuedCoupon coupon = issuedCouponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
        if (!coupon.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다");
        }
        coupon.validateUsable();
        return coupon;
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> findActiveByUserId(Long userId, Pageable pageable) {
        return issuedCouponRepository.findActiveByUserId(userId, pageable);
    }
}
