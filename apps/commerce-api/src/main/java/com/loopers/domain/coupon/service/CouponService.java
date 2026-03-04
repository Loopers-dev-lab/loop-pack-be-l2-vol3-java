package com.loopers.domain.coupon.service;

import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.domain.coupon.repository.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    // === CouponTemplate 관련 ===

    public CouponTemplate createTemplate(CouponCommand.CreateTemplate command) {
        CouponTemplate template = CouponTemplate.create(command);
        return couponTemplateRepository.save(template);
    }

    public CouponTemplate getTemplate(Long templateId) {
        return couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));
    }

    public Page<CouponTemplate> getTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    public CouponTemplate updateTemplate(Long templateId, CouponCommand.UpdateTemplate command) {
        CouponTemplate template = getTemplate(templateId);
        template.update(command);
        couponTemplateRepository.update(template);
        return template;
    }

    public void deleteTemplate(Long templateId) {
        getTemplate(templateId);
        couponTemplateRepository.deleteById(templateId);
    }

    // === UserCoupon 관련 ===

    public UserCoupon issueCoupon(Long couponTemplateId, Long memberId) {
        CouponTemplate template = getTemplate(couponTemplateId);

        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        if (userCouponRepository.existsByMemberIdAndCouponTemplateId(memberId, couponTemplateId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        UserCoupon userCoupon = UserCoupon.issue(couponTemplateId, memberId);
        return userCouponRepository.save(userCoupon);
    }

    public Page<UserCoupon> getUserCoupons(Long memberId, Pageable pageable) {
        return userCouponRepository.findByMemberId(memberId, pageable);
    }

    public Page<UserCouponItem> getUserCouponsWithTemplate(Long memberId, Pageable pageable) {
        return userCouponRepository.findByMemberIdWithTemplate(memberId, pageable);
    }

    public Page<UserCoupon> getIssuedCoupons(Long couponTemplateId, Pageable pageable) {
        return userCouponRepository.findByCouponTemplateId(couponTemplateId, pageable);
    }

    public CouponTemplate useUserCoupon(Long userCouponId, Long memberId, int orderAmount) {
        UserCoupon userCoupon = userCouponRepository.findByIdWithLock(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        userCoupon.validateOwnership(memberId);
        userCoupon.validateUsable();

        CouponTemplate template = getTemplate(userCoupon.getCouponTemplateId());

        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (orderAmount < template.getMinOrderAmount().value()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }

        userCoupon.use();
        userCouponRepository.update(userCoupon);

        return template;
    }
}
