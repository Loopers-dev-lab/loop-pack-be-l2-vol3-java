package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateEntity;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Transactional
    public void process(Long requestId, Long userId, Long templateId) {
        // 중복 발급 방지
        if (couponJpaRepository.existsByRefUserIdAndRefTemplateId(userId, templateId)) {
            throw new CouponIssueException("이미 발급된 쿠폰입니다.");
        }

        // 선착순 수량 제한 (atomic UPDATE)
        int updated = couponTemplateJpaRepository.tryIncrementIssuedCount(templateId);
        if (updated == 0) {
            throw new CouponIssueException("쿠폰 발급 수량이 초과되었습니다.");
        }

        // 쿠폰 발급 (템플릿 스냅샷 저장)
        CouponTemplateEntity template = couponTemplateJpaRepository.findById(templateId)
            .orElseThrow(() -> new CouponIssueException("쿠폰 템플릿을 찾을 수 없습니다."));

        couponJpaRepository.save(new CouponEntity(
            userId,
            templateId,
            template.getName(),
            template.getType(),
            template.getDiscountValue(),
            template.getMinOrderAmount(),
            template.getExpiredAt()
        ));

        couponIssueRequestJpaRepository.updateStatus(requestId, CouponIssueRequestStatus.SUCCESS, null);
        log.info("쿠폰 발급 성공. requestId={}, userId={}, templateId={}", requestId, userId, templateId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long requestId, String reason) {
        couponIssueRequestJpaRepository.updateStatus(requestId, CouponIssueRequestStatus.FAILED, reason);
        log.info("쿠폰 발급 실패 처리. requestId={}, reason={}", requestId, reason);
    }
}
