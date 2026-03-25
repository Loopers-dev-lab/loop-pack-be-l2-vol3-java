package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 선착순 쿠폰 발급 Consumer — commerce-api 내에서 실행
 *
 * 설계 근거:
 *   CouponService가 commerce-api에 있으므로 Consumer도 같은 앱에 배치.
 *   → 도메인 서비스 직접 접근 가능
 *   → 비즈니스 로직 중복 없음
 *   → Kafka의 이점(파티션 순차 처리, 비동기 버퍼링)은 유지
 *
 * 동시성 제어:
 *   key=couponTemplateId → 같은 쿠폰의 모든 요청이 같은 파티션
 *   → 1 Consumer가 순차 처리
 *   → 비관적 락 없이 수량 정합성 보장
 *
 * 멱등성:
 *   event_handled 테이블에 eventId 기록
 *   → 중복 소비 시 스킵
 */
@Component
public class CouponIssueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueConsumer.class);

    private final ObjectMapper objectMapper;
    private final CouponService couponService;
    private final CouponIssueRequestJpaRepository couponIssueRequestRepository;
    private final EventHandledJpaRepository eventHandledRepository;

    public CouponIssueConsumer(ObjectMapper objectMapper,
                                CouponService couponService,
                                CouponIssueRequestJpaRepository couponIssueRequestRepository,
                                EventHandledJpaRepository eventHandledRepository) {
        this.objectMapper = objectMapper;
        this.couponService = couponService;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.eventHandledRepository = eventHandledRepository;
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
            } catch (Exception e) {
                log.error("[CouponIssue] 처리 실패 — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
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

            // 발급 성공 → 요청 이력 업데이트
            request.markIssued(issued.getId());
            couponIssueRequestRepository.save(request);

            // 멱등성 기록 (같은 TX)
            eventHandledRepository.save(EventHandledEntity.of(eventId, "coupon-issue-requests-v1"));

            log.info("[CouponIssue] 발급 성공 — templateId={}, userId={}, issuedCouponId={}",
                    templateId, userId, issued.getId());

        } catch (Exception e) {
            // 발급 실패 (재고 소진, 중복 발급 등) → 요청 이력에 실패 기록
            request.markFailed(e.getMessage());
            couponIssueRequestRepository.save(request);

            // 실패해도 멱등성 기록 → 같은 요청 재처리 방지
            eventHandledRepository.save(EventHandledEntity.of(eventId, "coupon-issue-requests-v1"));

            log.warn("[CouponIssue] 발급 실패 — templateId={}, userId={}, reason={}",
                    templateId, userId, e.getMessage());
        }
    }
}
