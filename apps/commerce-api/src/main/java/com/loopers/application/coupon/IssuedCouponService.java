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

@Service
@RequiredArgsConstructor
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    // Command

    @Transactional
    public IssuedCoupon issue(Long couponId, Long userId) {
        if (issuedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다");
        }
        IssuedCoupon issuedCoupon = IssuedCoupon.create(couponId, userId);
        return issuedCouponRepository.save(issuedCoupon);
    }

    @Transactional
    public void deleteAvailableByCouponId(Long couponId) {
        List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findAllByCouponId(couponId);
        issuedCoupons.stream()
                .filter(ic -> !ic.isUsed())
                .forEach(IssuedCoupon::delete);
    }

    @Transactional
    public IssuedCoupon getIssuedCouponForUpdate(Long issuedCouponId) {
        return issuedCouponRepository.findByIdForUpdate(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다"));
    }

    // Query

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> findActiveByUserId(Long userId, Pageable pageable) {
        return issuedCouponRepository.findActiveByUserId(userId, pageable);
    }
}
