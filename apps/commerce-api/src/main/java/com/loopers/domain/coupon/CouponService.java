package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    public CouponService(CouponTemplateRepository couponTemplateRepository,
                         IssuedCouponRepository issuedCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.issuedCouponRepository = issuedCouponRepository;
    }

    @Transactional(timeout = 30)
    public IssuedCoupon issue(Long templateId, Long userId) {
        CouponTemplate template = couponTemplateRepository.findByIdForUpdate(templateId)
                .orElseThrow(() -> new CoreException(CouponErrorType.TEMPLATE_NOT_FOUND));

        if (template.getStatus() != CouponTemplateStatus.ACTIVE) {
            throw new CoreException(CouponErrorType.INVALID_TEMPLATE);
        }
        template.assertIssuable(java.time.ZonedDateTime.now());

        // 전체 발급 한도 체크 (Race Condition 해결: Template에 FOR UPDATE 락 + atomic increment)
        template.incrementIssuedCount();
        couponTemplateRepository.save(template);  // issuedCount 업데이트

        // 사용자별 발급 한도 체크 (비관적 락 내에서 수행)
        long userIssued = issuedCouponRepository.countByCouponTemplateIdAndUserId(templateId, userId);
        if (userIssued >= template.getMaxIssueCountPerUser()) {
            throw new CoreException(CouponErrorType.USER_ISSUE_LIMIT_EXCEEDED);
        }

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(templateId, userId,
                template.getName(), template.getDiscountType(),
                template.getDiscountValue(), template.getMaxDiscountAmount());
        return issuedCouponRepository.save(issuedCoupon);
    }

    /**
     * 쿠폰 사용 (POJO 검증 + 원자적 UPDATE)
     *
     * POJO로 소유권/상태를 빠르게 검증한 후,
     * SQL WHERE status='ISSUED' 조건으로 동시성을 보호한다.
     */
    @Transactional(timeout = 30)
    public void use(Long issuedCouponId, Long userId, Long orderId) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(CouponErrorType.COUPON_NOT_FOUND));
        issuedCoupon.validateOwnership(userId);
        issuedCoupon.validateUsable();

        int affected = issuedCouponRepository.useAtomically(issuedCouponId, orderId, ZonedDateTime.now());
        if (affected == 0) {
            throw new CoreException(CouponErrorType.COUPON_ALREADY_USED);
        }
    }

    /**
     * 쿠폰 복원 — 보상 트랜잭션용 (USED → ISSUED)
     *
     * PG 결제 실패 시 사용했던 쿠폰을 원래 상태로 되돌린다.
     * SQL WHERE status='USED' 조건으로 안전하게 복원한다.
     */
    @Transactional(timeout = 30)
    public void restore(Long issuedCouponId) {
        int affected = issuedCouponRepository.restoreAtomically(issuedCouponId);
        if (affected == 0) {
            throw new CoreException(CouponErrorType.INVALID_COUPON_STATUS);
        }
    }

    @Transactional(readOnly = true)
    public IssuedCoupon getIssuedCoupon(Long issuedCouponId, Long userId) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(CouponErrorType.COUPON_NOT_FOUND));
        issuedCoupon.validateOwnership(userId);
        return issuedCoupon;
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

    @Transactional(readOnly = true)
    public List<CouponTemplate> getTemplatesByIds(Set<Long> ids) {
        return couponTemplateRepository.findAllByIdIn(ids);
    }

    @Transactional(readOnly = true)
    public List<CouponTemplate> getIssuableTemplates() {
        return couponTemplateRepository.findAllIssuable();
    }

    @Transactional(readOnly = true)
    public List<IssuedCoupon> getIssuedCouponsByTemplateId(Long couponTemplateId) {
        return issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId);
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

    @Transactional(timeout = 30)
    public CouponTemplate createTemplate(String name, String description, DiscountType discountType,
                                          int discountValue, Integer maxDiscountAmount, int minOrderAmount,
                                          int maxIssueCount, int maxIssueCountPerUser,
                                          java.time.ZonedDateTime validFrom, java.time.ZonedDateTime validTo) {
        CouponTemplate template = CouponTemplate.define(name, description, discountType, discountValue,
                maxDiscountAmount, minOrderAmount, maxIssueCount, maxIssueCountPerUser, validFrom, validTo);
        return couponTemplateRepository.save(template);
    }

    @Transactional(timeout = 30)
    public CouponTemplate updateTemplate(Long templateId, String name, String description,
                                          DiscountType discountType, Integer discountValue,
                                          Integer maxDiscountAmount, Integer minOrderAmount) {
        CouponTemplate template = getTemplate(templateId);
        template.changeDetails(name, description, discountType, discountValue, maxDiscountAmount, minOrderAmount);
        return couponTemplateRepository.save(template);
    }

    @Transactional(timeout = 30)
    public void deleteTemplate(Long templateId) {
        CouponTemplate template = getTemplate(templateId);
        template.withdraw();
        couponTemplateRepository.save(template);
    }
}