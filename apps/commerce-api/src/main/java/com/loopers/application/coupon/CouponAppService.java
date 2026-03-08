package com.loopers.application.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponAppService {
    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public Coupon create(String name, DiscountType discountType, Money discountValue,
                         Money minOrderAmount, Money maxDiscountAmount,
                         int totalQuantity, ZonedDateTime validFrom, ZonedDateTime validUntil) {
        Coupon coupon = Coupon.create(name, discountType, discountValue,
                minOrderAmount, maxDiscountAmount, totalQuantity, validFrom, validUntil);
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Coupon getById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Coupon> getAll() {
        return couponRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Coupon> getAll(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional
    public Coupon update(Long id, String name, DiscountType discountType, Money discountValue,
                         Money minOrderAmount, Money maxDiscountAmount,
                         int totalQuantity, ZonedDateTime validFrom, ZonedDateTime validUntil) {
        Coupon coupon = couponRepository.findByIdWithLock(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.update(name, discountType, discountValue, minOrderAmount, maxDiscountAmount,
                totalQuantity, validFrom, validUntil);
        return coupon;
    }

    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findByIdWithLock(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.delete();
    }

    @Transactional
    public IssuedCoupon getIssuedCouponWithLock(Long couponId, Long userId) {
        return issuedCouponRepository.findByCouponIdAndUserIdWithLock(couponId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional
    public IssuedCoupon getIssuedCouponByIdWithLock(Long id) {
        return issuedCouponRepository.findByIdWithLock(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
    }

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
                    String couponName = coupon != null ? coupon.getName() : "(삭제된 쿠폰)";
                    return IssuedCouponInfo.of(ic, couponName);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IssuedCoupon> getIssuedCouponsByUserId(Long userId) {
        return issuedCouponRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<IssuedCoupon> getIssuedCouponsByCouponId(Long couponId) {
        return issuedCouponRepository.findByCouponId(couponId);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> getIssuedCouponsByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findByCouponId(couponId, pageable);
    }
}
