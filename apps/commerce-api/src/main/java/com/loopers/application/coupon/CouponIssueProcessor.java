package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.support.error.CoreException;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 처리 — @Transactional 보장
 *
 * Consumer(Interfaces)에서 분리된 비즈니스 처리 Bean.
 *   → 프록시를 통한 호출 → @Transactional 정상 동작
 *   → 쿠폰 발급 + 상태 업데이트 + 멱등성 기록이 같은 TX
 *
 * 비즈니스 실패(재고 소진, 중복 발급)는 BusinessFailureException으로 래핑:
 *   → Consumer에서 재시도 없이 즉시 처리 (이미 FAILED 기록 완료)
 */
@Service
public class CouponIssueProcessor {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueProcessor.class);

    private final ObjectMapper objectMapper;
    private final CouponService couponService;
    private final CouponIssueRequestJpaRepository couponIssueRequestRepository;
    private final EventHandledJpaRepository eventHandledRepository;

    public CouponIssueProcessor(ObjectMapper objectMapper,
                                 CouponService couponService,
                                 CouponIssueRequestJpaRepository couponIssueRequestRepository,
                                 EventHandledJpaRepository eventHandledRepository) {
        this.objectMapper = objectMapper;
        this.couponService = couponService;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.eventHandledRepository = eventHandledRepository;
    }

    /**
     * 쿠폰 발급 요청 처리
     *
     * @throws BusinessFailureException 비즈니스 실패 (재고 소진 등) — 재시도 불필요
     * @throws RuntimeException 인프라 장애 — ErrorHandler가 DLQ로 격리
     */
    @Transactional
    public void process(String payload) {
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("[CouponProcessor] JSON 파싱 실패 — payload={}", payload, e);
            return;
        }

        String eventId = node.path("eventId").asText(null);
        Long requestId = node.path("requestId").asLong(0);
        Long templateId = node.path("templateId").asLong(0);
        Long userId = node.path("userId").asLong(0);

        if (eventId == null || requestId == 0) {
            log.warn("[CouponProcessor] 필수 필드 누락 — payload={}", payload);
            return;
        }

        // 멱등성 체크
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.warn("[CouponProcessor] 중복 스킵 — eventId={}", eventId);
            return;
        }

        // 발급 요청 조회
        CouponIssueRequestEntity request = couponIssueRequestRepository.findById(requestId)
                .orElse(null);
        if (request == null) {
            log.error("[CouponProcessor] 발급 요청 없음 — requestId={}", requestId);
            return;
        }

        // 쿠폰 발급 시도
        try {
            IssuedCoupon issued = couponService.issue(templateId, userId);

            request.markIssued(issued.getId());
            couponIssueRequestRepository.save(request);
            eventHandledRepository.save(EventHandledEntity.of(eventId, "coupon-issue-requests-v1"));

            log.info("[CouponProcessor] 발급 성공 — templateId={}, userId={}, issuedCouponId={}",
                    templateId, userId, issued.getId());

        } catch (CoreException e) {
            // 비즈니스 실패 (재고 소진, 발급 한도 초과 등)
            // CouponService.issue()가 같은 TX에 참여하므로, CoreException throw 시
            // TX가 rollback-only로 마킹된다. 여기서 markFailed()를 해도 커밋 시 롤백된다.
            // → Consumer에서 별도 TX로 FAILED 기록을 위임한다.
            throw new BusinessFailureException(
                    e.getMessage(), e, requestId, eventId);
        }
    }

    /**
     * 비즈니스 실패 시 FAILED 기록 — 별도 TX (REQUIRES_NEW)
     *
     * process()의 TX가 rollback-only 상태이므로, 새 TX에서 FAILED + event_handled를 저장한다.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markFailedInNewTx(Long requestId, String eventId, String reason) {
        CouponIssueRequestEntity request = couponIssueRequestRepository.findById(requestId)
                .orElse(null);
        if (request != null) {
            request.markFailed(reason);
            couponIssueRequestRepository.save(request);
        }
        eventHandledRepository.save(EventHandledEntity.of(eventId, "coupon-issue-requests-v1"));
    }

    public static class BusinessFailureException extends RuntimeException {
        private final Long requestId;
        private final String eventId;

        public BusinessFailureException(String message, Throwable cause, Long requestId, String eventId) {
            super(message, cause);
            this.requestId = requestId;
            this.eventId = eventId;
        }

        public Long getRequestId() { return requestId; }
        public String getEventId() { return eventId; }
    }
}
