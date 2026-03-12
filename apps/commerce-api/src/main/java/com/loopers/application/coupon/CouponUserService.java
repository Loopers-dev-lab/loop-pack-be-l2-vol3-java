package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 사용자 서비스.
 */
@Service
@RequiredArgsConstructor
public class CouponUserService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    /**
     * 쿠폰 발급.
     */
    @Transactional
    public IssuedCouponResult issue(Long userId, Long couponTemplateId) {
        // 1. 쿠폰 템플릿 조회 (비관적 락)
        CouponTemplate template = couponTemplateRepository.findByIdWithLock(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        // 2. 중복 발급 확인
        if (issuedCouponRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
            throw new CoreException(ErrorType.COUPON_ALREADY_ISSUED);
        }

        // 3. 발급 가능 여부 확인 및 발급 수 증가
        template.incrementIssuedCount();
        couponTemplateRepository.save(template);

        // 4. 발급된 쿠폰 생성
        IssuedCoupon issued = IssuedCoupon.create(userId, template);
        IssuedCoupon saved = issuedCouponRepository.save(issued);

        return IssuedCouponResult.from(saved, template);
    }

    /**
     * 내 쿠폰 목록 조회.
     */
    @Transactional(readOnly = true)
    public List<IssuedCouponResult> findMyCoupons(Long userId, int offset, int limit) {
        List<IssuedCoupon> coupons = issuedCouponRepository.findAllByUserId(userId, offset, limit);

        return coupons.stream()
            .map(coupon -> {
                CouponTemplate template = couponTemplateRepository.findById(coupon.getCouponTemplateId())
                    .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
                return IssuedCouponResult.from(coupon, template);
            })
            .toList();
    }

    /**
     * 내 쿠폰 상세 조회.
     */
    @Transactional(readOnly = true)
    public IssuedCouponResult findMyCouponById(Long userId, Long couponId) {
        IssuedCoupon coupon = issuedCouponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        if (!coupon.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.COUPON_ACCESS_DENIED);
        }

        CouponTemplate template = couponTemplateRepository.findById(coupon.getCouponTemplateId())
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        return IssuedCouponResult.from(coupon, template);
    }

    /**
     * 내 쿠폰 수 조회.
     */
    @Transactional(readOnly = true)
    public long countMyCoupons(Long userId) {
        return issuedCouponRepository.countByUserId(userId);
    }
}
