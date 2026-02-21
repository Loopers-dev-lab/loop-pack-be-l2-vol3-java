package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    public CouponService(CouponTemplateRepository couponTemplateRepository, IssuedCouponRepository issuedCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.issuedCouponRepository = issuedCouponRepository;
    }

    public IssuedCoupon issue(Long templateId, Long userId) {
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new CoreException(CouponErrorType.TEMPLATE_NOT_FOUND));

        if (template.getStatus() != CouponTemplateStatus.ACTIVE) {
            throw new CoreException(CouponErrorType.INVALID_TEMPLATE);
        }

        long totalIssued = issuedCouponRepository.countByCouponTemplateId(templateId);
        if (totalIssued >= template.getMaxIssueCount()) {
            throw new CoreException(CouponErrorType.ISSUE_LIMIT_EXCEEDED);
        }

        long userIssued = issuedCouponRepository.countByCouponTemplateIdAndUserId(templateId, userId);
        if (userIssued >= template.getMaxIssueCountPerUser()) {
            throw new CoreException(CouponErrorType.USER_ISSUE_LIMIT_EXCEEDED);
        }

        IssuedCoupon issuedCoupon = IssuedCoupon.create(templateId, userId);
        return issuedCouponRepository.save(issuedCoupon);
    }

    public void use(Long issuedCouponId, Long userId, Long orderId) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(CouponErrorType.COUPON_NOT_FOUND));
        issuedCoupon.validateOwnership(userId);
        issuedCoupon.use(orderId);
    }
}
