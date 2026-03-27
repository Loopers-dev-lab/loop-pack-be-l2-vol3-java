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
 * 선착순 쿠폰 발급 Consumer — Spring ErrorHandler 기반 DLQ 처리
 *
 * 재시도 로직 제거 + Spring Kafka ErrorHandler 위임:
 *   1. 재시도 없음 (MAX_RETRY = 0) — 배치 블로킹 방지
 *   2. Spring ErrorHandler가 즉시 DLQ 격리 (동기 전송)
 *   3. 멱등성 (event_handled) — 중복 처리 방지
 *
 * 비즈니스 실패(재고 소진, 중복 발급)는 재시도 대상이 아님:
 *   → 재시도해도 결과가 같으므로 즉시 FAILED 처리
 *   → 인프라 장애도 ErrorHandler가 DLQ로 즉시 격리
 *
 * 배치 블로킹 방지:
 *   - 기존: 한 레코드 실패 시 7초 재시도 → 나머지 배치 블로킹
 *   - 개선: 한 레코드 실패 시 즉시 DLQ → 나머지 배치 정상 처리
 */
@Component
public class CouponIssueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);
    private static final int MAX_RETRY = 0;  // 재시도 제거 (ErrorHandler가 처리)

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
                processRecord(record);
            } catch (BusinessFailureException e) {
                // 비즈니스 실패 → 재시도 불필요 (재고 소진, 중복 발급 등)
                log.warn("[CouponIssue] 비즈니스 실패 (재시도 불필요) — error={}", e.getMessage());
                // 이미 FAILED로 기록됨, ACK 진행
            } catch (Exception e) {
                // 인프라 장애 → Spring ErrorHandler가 DLQ로 전송
                log.error("[CouponIssue] 인프라 실패 → ErrorHandler 위임 — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage());
                throw e;  // ErrorHandler에게 위임
            }
        }
        ack.acknowledge();
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
