package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.dlq.DlqPublisher;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 선착순 쿠폰 발급 Consumer — 재시도 + 지수 백오프 + DLQ
 *
 * kafka-pipeline-lab에서 배운 패턴 적용:
 *   1. 재시도 3회 + 지수 백오프 (1초→2초→4초) — 일시적 장애 복구
 *   2. 3회 실패 → DLQ 격리 (동기 전송) — 영구 실패 메시지 보존
 *   3. 멱등성 (event_handled) — 중복 처리 방지
 *
 * 비즈니스 실패(재고 소진, 중복 발급)는 재시도 대상이 아님:
 *   → 재시도해도 결과가 같으므로 즉시 FAILED 처리
 *   → 재시도 대상: 인프라 장애 (DB 커넥션, 네트워크 순단)
 */
@Component
public class CouponIssueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);
    private static final int MAX_RETRY = 3;

    private final ObjectMapper objectMapper;
    private final CouponService couponService;
    private final CouponIssueRequestJpaRepository couponIssueRequestRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final DlqPublisher dlqPublisher;

    public CouponIssueConsumer(ObjectMapper objectMapper,
                                CouponService couponService,
                                CouponIssueRequestJpaRepository couponIssueRequestRepository,
                                EventHandledJpaRepository eventHandledRepository,
                                DlqPublisher dlqPublisher) {
        this.objectMapper = objectMapper;
        this.couponService = couponService;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.eventHandledRepository = eventHandledRepository;
        this.dlqPublisher = dlqPublisher;
    }

    @KafkaListener(
            topics = "coupon-issue-requests-v1",
            groupId = "coupon-issue-group",
            containerFactory = "BATCH_LISTENER_DEFAULT"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                processWithRetry(record);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[CouponIssue] 처리 중단 — partition={}, offset={}",
                        record.partition(), record.offset());
            } catch (Exception e) {
                log.error("[CouponIssue] 예외 → DLQ — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
                dlqPublisher.sendToDlq(record, e, 0);
            }
        }
        ack.acknowledge();
    }

    /**
     * 재시도 + 지수 백오프
     *
     * 비즈니스 실패(재고 소진 등)는 재시도하지 않음 — 결과가 같으므로
     * 인프라 장애(DB, 네트워크)만 재시도 대상
     */
    private void processWithRetry(ConsumerRecord<Object, Object> record) throws InterruptedException {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                processRecord(record);
                return;  // 성공 → 종료
            } catch (BusinessFailureException e) {
                // 비즈니스 실패 → 재시도 불필요 (재고 소진, 중복 발급 등)
                log.warn("[CouponIssue] 비즈니스 실패 (재시도 불필요) — error={}", e.getMessage());
                return;  // 재시도 없이 종료 (이미 FAILED로 기록됨)
            } catch (Exception e) {
                lastException = e;
                log.warn("[CouponIssue] 재시도 {}/{} 실패 — partition={}, offset={}, error={}",
                        attempt, MAX_RETRY, record.partition(), record.offset(), e.getMessage());

                if (attempt < MAX_RETRY) {
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;  // 1초→2초→4초
                    log.info("[CouponIssue] {}ms 후 재시도...", backoffMs);
                    Thread.sleep(backoffMs);
                }
            }
        }

        // 3회 모두 실패 → DLQ
        log.error("[CouponIssue] {}회 재시도 모두 실패 → DLQ — partition={}, offset={}",
                MAX_RETRY, record.partition(), record.offset());
        dlqPublisher.sendToDlq(record, lastException, MAX_RETRY);
    }

    @Transactional
    protected void processRecord(ConsumerRecord<Object, Object> record) {
        String payload = record.value().toString();

        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("[CouponIssue] JSON 파싱 실패 — payload={}", payload, e);
            return;
        }

        String eventId = node.path("eventId").asText(null);
        Long requestId = node.path("requestId").asLong(0);
        Long templateId = node.path("templateId").asLong(0);
        Long userId = node.path("userId").asLong(0);

        if (eventId == null || requestId == 0) {
            log.warn("[CouponIssue] 필수 필드 누락 — payload={}", payload);
            return;
        }

        // 멱등성 체크
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.warn("[CouponIssue] 중복 스킵 — eventId={}", eventId);
            return;
        }

        // 발급 요청 조회
        CouponIssueRequestEntity request = couponIssueRequestRepository.findById(requestId)
                .orElse(null);
        if (request == null) {
            log.error("[CouponIssue] 발급 요청 없음 — requestId={}", requestId);
            return;
        }

        // 쿠폰 발급 시도
        try {
            IssuedCoupon issued = couponService.issue(templateId, userId);

            request.markIssued(issued.getId());
            couponIssueRequestRepository.save(request);
            eventHandledRepository.save(EventHandledEntity.of(eventId, "coupon-issue-requests-v1"));

            log.info("[CouponIssue] 발급 성공 — templateId={}, userId={}, issuedCouponId={}",
                    templateId, userId, issued.getId());

        } catch (Exception e) {
            // 비즈니스 실패 → FAILED 기록 + 멱등성 기록
            request.markFailed(e.getMessage());
            couponIssueRequestRepository.save(request);
            eventHandledRepository.save(EventHandledEntity.of(eventId, "coupon-issue-requests-v1"));

            // BusinessFailureException으로 래핑하여 재시도 루프에서 구분
            throw new BusinessFailureException(e.getMessage(), e);
        }
    }

    /**
     * 비즈니스 로직 실패를 구분하기 위한 예외
     * 재고 소진, 중복 발급 등 재시도해도 결과가 같은 실패
     */
    static class BusinessFailureException extends RuntimeException {
        BusinessFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
