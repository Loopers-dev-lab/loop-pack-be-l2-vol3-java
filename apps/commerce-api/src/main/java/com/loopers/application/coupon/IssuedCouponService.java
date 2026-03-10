package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional(readOnly = true)
    public List<IssuedCouponInfo> getIssuedCoupons(Long userId) {
        return issuedCouponRepository.findByUserId(userId)
                                     .stream()
                                     .map(IssuedCouponInfo::from)
                                     .toList();
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findByCouponId(couponId, pageable)
                                     .map(IssuedCouponInfo::from);
    }

    @Transactional(readOnly = true)
    public IssuedCouponInfo getUsableBy(Long issuedCouponId, Long userId) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findByIdAndUserId(issuedCouponId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[issuedCouponId = " + issuedCouponId + "] 를 찾을 수 없습니다."));
        issuedCoupon.validateUsableBy(userId);

        return IssuedCouponInfo.from(issuedCoupon);
    }

    @Transactional
    public void use(Long issuedCouponId, Long userId) {
        int affected = issuedCouponRepository.useById(issuedCouponId, userId);
        if (affected == 0) {
            throw new CoreException(ErrorType.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다.");
        }
    }
}
