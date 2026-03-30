package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public CouponModel getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional
    public CouponModel getCouponForUpdate(Long couponId) {
        return couponRepository.findByIdForUpdate(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponModel> getAll(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional
    public CouponModel register(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt, Long issueLimit) {
        CouponModel coupon = new CouponModel(name, type, value, minOrderAmount, expiredAt, issueLimit);
        return couponRepository.save(coupon);
    }

    @Transactional
    public CouponModel update(Long couponId, String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt, Long issueLimit) {
        CouponModel coupon = getCoupon(couponId);
        coupon.update(name, type, value, minOrderAmount, expiredAt, issueLimit);
        return coupon;
    }

    @Transactional
    public void delete(Long couponId) {
        CouponModel coupon = getCoupon(couponId);
        coupon.delete();
    }
}
