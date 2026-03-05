package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public Coupon create(String name, Coupon.DiscountType discountType, Long discountValue, Long minOrderAmount, LocalDateTime expiresAt) {
        return couponRepository.save(
            Coupon.create(name, discountType, discountValue, minOrderAmount, expiresAt)
        );
    }

    @Transactional(readOnly = true)
    public Page<Coupon> findAll(Pageable pageable) {
        return couponRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Coupon findById(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[couponId = " + couponId + "] 를 찾을 수 없습니다."));

        if (coupon.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[couponId = " + couponId + "] 를 찾을 수 없습니다.");
        }

        return coupon;
    }

    @Transactional
    public Coupon update(Long couponId, String name, Long discountValue, Long minOrderAmount, LocalDateTime expiresAt) {
        Coupon coupon = findById(couponId);
        coupon.update(name, discountValue, minOrderAmount, expiresAt);
        return coupon;
    }

    @Transactional
    public void delete(Long couponId) {
        Coupon coupon = findById(couponId);
        coupon.delete();
    }

    @Transactional(readOnly = true)
    public long calculateDiscount(Long couponId, long orderAmount) {
        Coupon coupon = couponRepository.findById(couponId)
                                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[couponId = " + couponId + "] 를 찾을 수 없습니다."));
        return coupon.calculateDiscount(orderAmount);
    }
}
