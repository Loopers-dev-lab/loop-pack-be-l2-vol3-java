package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.EventHandled;
import com.loopers.infrastructure.EventHandledJpaRepository;
import com.loopers.kafka.event.CouponIssueRequestEvent;
import com.loopers.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 선착순 쿠폰 발급 Consumer
 *
 * 초과발급 방지: partitions=1 (couponId key) → 단일 Consumer 순차 처리
 * 중복발급 방지: event_handled 테이블 멱등성 체크
 * 이벤트 유실 방지: manual Ack (처리 완료 후 offset commit)
 *
 * 실제 발급(DB insert)은 commerce-api의 IssuedCoupon 도메인이 담당.
 * 현재는 실험 목적으로 카운터로 발급 수량을 추적한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private static final long MAX_ISSUE_COUNT = 100; // 선착순 100장

    private final EventHandledJpaRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    // 실험용 카운터 — 실제 환경에서는 DB issued_coupons 테이블 count로 대체
    private final AtomicLong issuedCount = new AtomicLong(0);

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTS, containerFactory = KafkaConfig.BATCH_LISTENER)
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                CouponIssueRequestEvent event = objectMapper.readValue(record.value().toString(), CouponIssueRequestEvent.class);

                // 1. 멱등성 체크 — offset commit 전 재시작으로 인한 중복 방지
                if (eventHandledRepository.existsById(event.eventId())) {
                    log.debug("[CouponIssue] duplicate skip eventId={}", event.eventId());
                    continue;
                }

                // 2. 수량 제한 — 선착순 100장 초과 시 skip
                long current = issuedCount.get();
                if (current >= MAX_ISSUE_COUNT) {
                    log.info("[CouponIssue] exhausted eventId={} issuedCount={}", event.eventId(), current);
                    eventHandledRepository.save(EventHandled.of(event.eventId()));
                    continue;
                }

                // 3. 발급 처리 (실제 환경: IssuedCoupon DB insert)
                issuedCount.incrementAndGet();
                eventHandledRepository.save(EventHandled.of(event.eventId()));
                log.info("[CouponIssue] issued eventId={} memberId={} couponTemplateId={} total={}",
                    event.eventId(), event.memberId(), event.couponTemplateId(), issuedCount.get());

            } catch (Exception e) {
                log.error("[CouponIssue] failed record={} cause={}", record, e.getMessage());
            }
        }
        ack.acknowledge();
    }

    public long getIssuedCount() { return issuedCount.get(); }
}
