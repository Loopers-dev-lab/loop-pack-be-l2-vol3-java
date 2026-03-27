package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.CouponIssueCount;
import com.loopers.domain.EventHandled;
import com.loopers.infrastructure.CouponIssueCountJpaRepository;
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
 * 초과발급 방지: partitions=1 + couponId key → 단일 Consumer 순차 처리
 * 중복발급 방지: event_handled 테이블 멱등성 체크
 * 이벤트 유실 방지: manual Ack (처리 완료 후 offset commit)
 * 재시작 안전: 발급 수량을 DB(coupon_issue_count)로 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private static final long MAX_ISSUE_COUNT = 100;

    /**
     * 실험용 플래그
     * USE_ATOMIC_LONG=true  → AtomicLong (재시작 시 카운트 리셋 → 초과발급 재현)
     * USE_ATOMIC_LONG=false → DB 카운터 (재시작 후에도 유지 → 정확히 100장)
     */
    private static final boolean USE_ATOMIC_LONG =
        Boolean.parseBoolean(System.getenv().getOrDefault("USE_ATOMIC_LONG", "false"));

    private final AtomicLong atomicCount = new AtomicLong(0);

    private final EventHandledJpaRepository eventHandledRepository;
    private final CouponIssueCountJpaRepository couponIssueCountRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTS, containerFactory = KafkaConfig.BATCH_LISTENER)
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                CouponIssueRequestEvent event = objectMapper.readValue(record.value().toString(), CouponIssueRequestEvent.class);

                // 1. 멱등성 체크 — 재전달된 메시지 중복 처리 방지
                if (eventHandledRepository.existsById(event.eventId())) {
                    log.debug("[CouponIssue] duplicate skip eventId={}", event.eventId());
                    continue;
                }

                // 2. 수량 제한
                if (USE_ATOMIC_LONG) {
                    // [실험 1] AtomicLong — 재시작 시 카운트 리셋
                    long current = atomicCount.get();
                    if (current >= MAX_ISSUE_COUNT) {
                        log.info("[CouponIssue][AtomicLong] exhausted issuedCount={}", current);
                        eventHandledRepository.save(EventHandled.of(event.eventId()));
                        continue;
                    }
                    atomicCount.incrementAndGet();
                    eventHandledRepository.save(EventHandled.of(event.eventId()));
                    log.info("[CouponIssue][AtomicLong] issued eventId={} total={}", event.eventId(), atomicCount.get());
                } else {
                    // [정상] DB 카운터 — 재시작 후에도 카운트 유지
                    CouponIssueCount count = couponIssueCountRepository
                        .findById(event.couponTemplateId())
                        .orElseGet(() -> couponIssueCountRepository.save(CouponIssueCount.init(event.couponTemplateId())));

                    if (!count.tryIncrement(MAX_ISSUE_COUNT)) {
                        log.info("[CouponIssue][DB] exhausted issuedCount={}", count.getIssuedCount());
                        eventHandledRepository.save(EventHandled.of(event.eventId()));
                        continue;
                    }
                    eventHandledRepository.save(EventHandled.of(event.eventId()));
                    log.info("[CouponIssue][DB] issued eventId={} total={}", event.eventId(), count.getIssuedCount());
                }

            } catch (Exception e) {
                log.error("[CouponIssue] failed record={} cause={}", record, e.getMessage());
            }
        }
        ack.acknowledge();
    }
}
