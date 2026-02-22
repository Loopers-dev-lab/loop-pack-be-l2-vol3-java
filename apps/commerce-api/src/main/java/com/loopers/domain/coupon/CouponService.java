package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    public CouponService(CouponTemplateRepository couponTemplateRepository,
                         IssuedCouponRepository issuedCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.issuedCouponRepository = issuedCouponRepository;
    }

    @Transactional
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

    @Transactional
    public void use(Long issuedCouponId, Long userId, Long orderId) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(CouponErrorType.COUPON_NOT_FOUND));
        issuedCoupon.validateOwnership(userId);
        issuedCoupon.use(orderId);
    }

    @Transactional(readOnly = true)
    public List<IssuedCoupon> getUserCoupons(Long userId) {
        return issuedCouponRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public CouponTemplate getTemplate(Long templateId) {
        return couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new CoreException(CouponErrorType.TEMPLATE_NOT_FOUND));
    }

    // --- 어드민 기능 ---

    @Transactional(readOnly = true)
    public List<CouponTemplate> getAllTemplates(int page, int size) {
        return couponTemplateRepository.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public long countAllTemplates() {
        return couponTemplateRepository.count();
    }

    @Transactional
    public CouponTemplate createTemplate(String name, String description, DiscountType discountType,
                                          int discountValue, Integer maxDiscountAmount, int minOrderAmount,
                                          int maxIssueCount, int maxIssueCountPerUser,
                                          java.time.ZonedDateTime validFrom, java.time.ZonedDateTime validTo) {
        CouponTemplate template = CouponTemplate.create(name, description, discountType, discountValue,
                maxDiscountAmount, minOrderAmount, maxIssueCount, maxIssueCountPerUser, validFrom, validTo);
        return couponTemplateRepository.save(template);
    }

    @Transactional
    public CouponTemplate updateTemplate(Long templateId, String name, String description,
                                          DiscountType discountType, int discountValue,
                                          Integer maxDiscountAmount, int minOrderAmount) {
        CouponTemplate template = getTemplate(templateId);
        template.update(name, description, discountType, discountValue, maxDiscountAmount, minOrderAmount);
        return template;
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        CouponTemplate template = getTemplate(templateId);
        template.delete();
    }
}
