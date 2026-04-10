package com.loopers.application.coupon;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserService userService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponIssueInfo requestIssue(String loginId, String rawPassword, Long couponId) {
        User user = userService.authenticate(loginId, rawPassword);
        CouponIssueRequest request = couponService.createRequest(couponId, user.getId());

        // Outbox에 저장 (같은 트랜잭션) → 릴레이가 coupon-issue-requests 토픽으로 발행
        outboxEventPublisher.publish(
            KafkaTopics.COUPON_ISSUE_REQUESTS,
            couponId.toString(),  // key=couponId → 같은 쿠폰은 같은 파티션 → 순서 보장
            "COUPON_ISSUE_REQUESTED",
            Map.of("requestId", request.getId(), "couponId", couponId, "userId", user.getId())
        );

        return CouponIssueInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueInfo getIssueResult(String loginId, String rawPassword, Long requestId) {
        userService.authenticate(loginId, rawPassword);
        CouponIssueRequest request = couponService.getRequest(requestId);
        return CouponIssueInfo.from(request);
    }

    @Transactional
    public CouponInfo.TemplateInfo createCouponTemplate(CouponTemplateCommand command) {
        CouponTemplate template = couponTemplateRepository.save(
            new CouponTemplate(command.name(), command.type(), command.value(),
                command.minOrderAmount(), command.expiredAt())
        );
        return CouponInfo.TemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public Page<CouponInfo.TemplateInfo> getCouponTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable).map(CouponInfo.TemplateInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponInfo.TemplateInfo getCouponTemplate(Long couponId) {
        CouponTemplate template = couponTemplateRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
        return CouponInfo.TemplateInfo.from(template);
    }

    @Transactional
    public CouponInfo.TemplateInfo updateCouponTemplate(Long couponId, CouponTemplateCommand command) {
        CouponTemplate template = couponTemplateRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
        template.update(command.name(), command.type(), command.value(), command.minOrderAmount(), command.expiredAt());
        return CouponInfo.TemplateInfo.from(couponTemplateRepository.save(template));
    }

    @Transactional
    public void deleteCouponTemplate(Long couponId) {
        couponTemplateRepository.deleteById(couponId);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.IssuedInfo> getIssuedCoupons(Long couponId) {
        return issuedCouponRepository.findAllByCouponTemplateId(couponId).stream()
            .map(CouponInfo.IssuedInfo::from)
            .toList();
    }
}
