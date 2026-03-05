package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
public class CouponAdminFacade {

    private final CouponService couponService;

    // 쿠폰 템플릿 목록 조회 (US-C03)
    @Transactional(readOnly = true)
    public Page<CouponTemplateInfo> findAllTemplates(Pageable pageable) {
        return couponService.findAllTemplates(pageable).map(CouponTemplateInfo::from);
    }

    // 쿠폰 템플릿 상세 조회 (US-C04)
    @Transactional(readOnly = true)
    public CouponTemplateInfo findTemplateById(Long id) {
        return CouponTemplateInfo.from(couponService.findTemplateById(id));
    }

    // 쿠폰 템플릿 등록 (US-C05)
    @Transactional
    public CouponTemplateInfo register(CouponTemplateRegisterCommand command) {
        return CouponTemplateInfo.from(
                couponService.register(
                        command.name(),
                        command.type(),
                        command.value(),
                        command.minOrderAmount(),
                        command.expiredAt()
                )
        );
    }

    // 쿠폰 템플릿 수정 (US-C06)
    @Transactional
    public CouponTemplateInfo update(Long id, CouponTemplateUpdateCommand command) {
        return CouponTemplateInfo.from(
                couponService.update(
                        id,
                        command.name(),
                        command.type(),
                        command.value(),
                        command.minOrderAmount(),
                        command.expiredAt()
                )
        );
    }

    // 쿠폰 템플릿 삭제 (US-C07, BR-C05)
    @Transactional
    public void delete(Long id) {
        couponService.delete(id);
    }

    // 특정 쿠폰 템플릿의 발급 내역 조회 (US-C08)
    @Transactional(readOnly = true)
    public Page<UserCouponInfo> findIssuesByTemplateId(Long templateId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return couponService.findIssuesByTemplateId(templateId, pageable)
                .map(userCoupon -> UserCouponInfo.from(userCoupon, now));
    }
}
