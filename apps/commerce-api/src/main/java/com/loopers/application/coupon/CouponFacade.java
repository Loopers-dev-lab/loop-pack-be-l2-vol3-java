package com.loopers.application.coupon;

import com.loopers.domain.coupon.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponFacade {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    // ── Admin: 쿠폰 템플릿 CRUD ──

    @Transactional
    public Coupon createCoupon(String name, DiscountType discountType, int discountValue,
                               int minOrderAmount, ZonedDateTime expiredAt) {
        Coupon coupon = new Coupon(name, discountType, discountValue, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    public List<Coupon> getCoupons() {
        return couponRepository.findAll();
    }

    @Transactional
    public Coupon updateCoupon(Long couponId, String name, DiscountType discountType,
                               int discountValue, int minOrderAmount, ZonedDateTime expiredAt) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.changeName(name);
        coupon.changeDiscount(discountType, discountValue);
        coupon.changeMinOrderAmount(minOrderAmount);
        coupon.changeExpiredAt(expiredAt);
        return coupon;
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.delete();
    }

    // ── Admin: 발급 내역 조회 ──

    public List<CouponIssue> getCouponIssues(Long couponId) {
        couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return couponIssueRepository.findAllByCouponId(couponId);
    }

    // ── 대고객: 쿠폰 발급 ──

    @Transactional
    public CouponIssue issueCoupon(Long couponId, Long memberId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (ZonedDateTime.now().isAfter(coupon.getExpiredAt())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        CouponIssue couponIssue = new CouponIssue(couponId, memberId, coupon.getExpiredAt());
        return couponIssueRepository.save(couponIssue);
    }

    // ── 대고객: 내 쿠폰 목록 ──

    public List<CouponIssue> getMyCoupons(Long memberId) {
        return couponIssueRepository.findAllByMemberId(memberId);
    }
}
