package com.loopers.application.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 관리자 서비스.
 */
@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    /**
     * 쿠폰 생성.
     */
    @Transactional
    public CouponTemplateResult create(CreateCouponRequest request) {
        CouponTemplate template;

        if (request.type() == CouponType.FIXED) {
            template = CouponTemplate.createFixed(
                request.name(),
                new Money(request.value()),
                new Money(request.minOrderAmount()),
                request.maxIssueCount(),
                request.expiredAt()
            );
        } else {
            template = CouponTemplate.createRate(
                request.name(),
                request.value().intValue(),
                new Money(request.minOrderAmount()),
                request.maxDiscountAmount(),
                request.maxIssueCount(),
                request.expiredAt()
            );
        }

        CouponTemplate saved = couponTemplateRepository.save(template);
        return CouponTemplateResult.from(saved);
    }

    /**
     * 쿠폰 조회.
     */
    @Transactional(readOnly = true)
    public CouponTemplateResult findById(Long id) {
        CouponTemplate template = couponTemplateRepository.findByIdActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        return CouponTemplateResult.from(template);
    }

    /**
     * 활성 쿠폰 목록 조회.
     */
    @Transactional(readOnly = true)
    public Page<CouponTemplateResult> findAll(Pageable pageable) {
        return couponTemplateRepository.findAllActive(pageable)
            .map(CouponTemplateResult::from);
    }

    /**
     * 쿠폰 삭제.
     */
    @Transactional
    public void delete(Long id) {
        CouponTemplate template = couponTemplateRepository.findByIdActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        template.delete();
        couponTemplateRepository.save(template);
    }

    /**
     * 쿠폰 발급 내역 조회.
     */
    @Transactional(readOnly = true)
    public List<IssuedCouponResult> findIssues(Long couponId, int offset, int limit) {
        couponTemplateRepository.findByIdActive(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        return issuedCouponRepository.findAllByCouponTemplateId(couponId, offset, limit).stream()
            .map(IssuedCouponResult::simple)
            .toList();
    }

    /**
     * 쿠폰 발급 수 조회.
     */
    @Transactional(readOnly = true)
    public long countIssues(Long couponId) {
        return issuedCouponRepository.countByCouponTemplateId(couponId);
    }
}
