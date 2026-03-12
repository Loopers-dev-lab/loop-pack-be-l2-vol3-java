package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.CouponCriteria;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.domain.coupon.CouponService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    @Transactional
    public CouponResult.Detail registerCoupon(CouponCriteria.Create criteria) {
        return CouponResult.Detail.from(
                couponService.register(criteria.toCommand()));
    }

    @Transactional(readOnly = true)
    public CouponResult.Detail getCoupon(Long couponId) {
        return CouponResult.Detail.from(
                couponService.getById(couponId));
    }

    @Transactional(readOnly = true)
    public Page<CouponResult.Detail> getCoupons(Pageable pageable) {
        return couponService.getAll(pageable)
                .map(CouponResult.Detail::from);
    }

    @Transactional
    public void updateCoupon(Long couponId, CouponCriteria.Update criteria) {
        couponService.update(couponId, criteria.toCommand());
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        couponService.delete(couponId);
    }

    @Transactional(readOnly = true)
    public Page<CouponResult.IssuedDetail> getIssuedCoupons(Long couponId, Pageable pageable) {
        return couponService.getIssuedCoupons(couponId, pageable)
                .map(CouponResult.IssuedDetail::from);
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 50,
            backoff = @Backoff(delay = 50, random = true))
    @Transactional
    public CouponResult.IssuedDetail issueCoupon(Long couponId, Long userId) {
        return CouponResult.IssuedDetail.from(
                couponService.issue(couponId, userId));
    }

    @Transactional(readOnly = true)
    public List<CouponResult.OwnedDetail> getMyOwnedCoupons(Long userId) {
        return couponService.getMyOwnedCoupons(userId).stream()
                .map(CouponResult.OwnedDetail::from)
                .toList();
    }
}
