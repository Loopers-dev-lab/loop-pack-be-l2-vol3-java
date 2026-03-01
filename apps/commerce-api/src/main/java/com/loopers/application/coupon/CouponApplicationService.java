package com.loopers.application.coupon;

import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponApplicationService {

    private final CouponDomainService couponDomainService;
    private final CouponIssueDomainService couponIssueDomainService;

    @Transactional
    public CouponIssue issueCoupon(Long couponId, Long userId) {
        Coupon coupon = couponDomainService.getById(couponId);
        return couponIssueDomainService.issue(coupon, userId);
    }

    @Transactional(readOnly = true)
    public List<CouponIssue> getMyIssues(Long userId) {
        return couponIssueDomainService.getMyIssues(userId);
    }

    @Transactional
    public Coupon registerCoupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        return couponDomainService.register(name, type, value, minOrderAmount, expiredAt);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long id) {
        return couponDomainService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<Coupon> getAllCoupons(int page, int size) {
        return couponDomainService.getAll(page, size);
    }

    @Transactional
    public Coupon updateCoupon(Long id, String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        return couponDomainService.update(id, name, type, value, minOrderAmount, expiredAt);
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponDomainService.delete(id);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponIssue> getCouponIssues(Long couponId, int page, int size) {
        return couponIssueDomainService.getIssuesByCouponId(couponId, page, size);
    }
}
