package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 쿠폰 Facade
 *
 * CouponService를 조합하여 사용자 쿠폰 관련 유스케이스를 처리한다.
 */
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueRequestJpaRepository couponIssueRequestRepository;
    private final OutboxEventService outboxEventService;

    public CouponFacade(CouponService couponService,
                        CouponIssueRequestJpaRepository couponIssueRequestRepository,
                        OutboxEventService outboxEventService) {
        this.couponService = couponService;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.outboxEventService = outboxEventService;
    }

    /**
     * 선착순 쿠폰 발급 요청 (비동기 — Kafka 기반)
     *
     * 1. 발급 요청 이력을 DB에 PENDING으로 저장
     * 2. Outbox에 이벤트 저장 (같은 TX — 원자성)
     * 3. 즉시 202 응답 → 유저가 결과를 폴링
     * 4. Relay → Kafka → CouponIssueConsumer가 실제 발급
     * 5. Consumer가 요청 이력을 ISSUED/FAILED로 업데이트
     */
    @Transactional
    public CouponIssueRequestResult requestCouponIssue(Long templateId, Long userId) {
        String eventId = java.util.UUID.randomUUID().toString();

        // 발급 요청 이력 저장 — 폴링 대상 + 추적용
        CouponIssueRequestEntity request = CouponIssueRequestEntity.create(templateId, userId, eventId);
        couponIssueRequestRepository.save(request);

        // Outbox 저장 — 같은 TX (비즈니스 + Outbox 원자성)
        outboxEventService.save(
                "COUPON", templateId,
                "CouponIssueRequestedEvent",
                new CouponIssueRequestPayload(request.getId(), templateId, userId, eventId),
                "coupon-issue-requests-v1",
                String.valueOf(templateId)  // key=couponTemplateId → 같은 쿠폰은 같은 파티션
        );

        return new CouponIssueRequestResult(request.getId(), eventId, "PENDING");
    }

    /** 발급 결과 폴링 */
    @Transactional(readOnly = true)
    public CouponIssueRequestResult getCouponIssueResult(Long requestId) {
        CouponIssueRequestEntity request = couponIssueRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("발급 요청을 찾을 수 없습니다: " + requestId));
        return new CouponIssueRequestResult(request.getId(), request.getEventId(), request.getStatus().name());
    }

    public record CouponIssueRequestPayload(Long requestId, Long templateId, Long userId, String eventId) {}
    public record CouponIssueRequestResult(Long requestId, String eventId, String status) {}

    /** 내 쿠폰 목록 조회 */
    @Transactional(readOnly = true)
    public CouponListResult getMyCoupons(Long userId) {
        List<IssuedCoupon> coupons = couponService.getUserCoupons(userId);

        Set<Long> templateIds = coupons.stream()
                .map(IssuedCoupon::getCouponTemplateId)
                .collect(Collectors.toSet());

        Map<Long, CouponTemplate> templateMap = couponService.getTemplatesByIds(templateIds).stream()
                .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));

        List<IssuedCouponDetail> details = coupons.stream()
                .map(c -> {
                    CouponTemplate t = templateMap.get(c.getCouponTemplateId());
                    return new IssuedCouponDetail(
                            c.getId(), c.getCouponTemplateId(),
                            t.getName(), t.getDiscountType().name(),
                            t.getDiscountValue(), t.getMaxDiscountAmount(),
                            c.getStatus().name(), c.getUsedAt(), c.getCreatedAt());
                })
                .toList();

        return new CouponListResult(details);
    }

    /** 발급 가능한 쿠폰 목록 조회 */
    @Transactional(readOnly = true)
    public AvailableCouponListResult getAvailableCoupons() {
        List<CouponTemplate> templates = couponService.getIssuableTemplates();
        List<AvailableCouponDetail> details = templates.stream()
                .map(t -> new AvailableCouponDetail(
                        t.getId(), t.getName(), t.getDescription(),
                        t.getDiscountType().name(), t.getDiscountValue(),
                        t.getMaxDiscountAmount(), t.getMinOrderAmount(),
                        t.getValidFrom(), t.getValidTo()))
                .toList();
        return new AvailableCouponListResult(details);
    }

    public record IssuedCouponDetail(
            Long issuedCouponId, Long couponTemplateId,
            String couponName, String discountType,
            int discountValue, Integer maxDiscountAmount,
            String status, ZonedDateTime usedAt, ZonedDateTime createdAt) {}

    public record CouponListResult(List<IssuedCouponDetail> coupons) {}

    public record AvailableCouponDetail(
            Long couponTemplateId, String name, String description,
            String discountType, int discountValue,
            Integer maxDiscountAmount, int minOrderAmount,
            ZonedDateTime validFrom, ZonedDateTime validTo) {}

    public record AvailableCouponListResult(List<AvailableCouponDetail> coupons) {}
}
