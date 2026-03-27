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

                // 2. 수량 제한 — DB 기반 (재시작 후에도 카운트 유지)
                CouponIssueCount count = couponIssueCountRepository
                    .findById(event.couponTemplateId())
                    .orElseGet(() -> couponIssueCountRepository.save(CouponIssueCount.init(event.couponTemplateId())));

                if (!count.tryIncrement(MAX_ISSUE_COUNT)) {
                    log.info("[CouponIssue] exhausted eventId={} issuedCount={}", event.eventId(), count.getIssuedCount());
                    eventHandledRepository.save(EventHandled.of(event.eventId()));
                    continue;
                }

                // 3. 발급 처리 완료 기록
                eventHandledRepository.save(EventHandled.of(event.eventId()));
                log.info("[CouponIssue] issued eventId={} memberId={} couponTemplateId={} total={}",
                    event.eventId(), event.memberId(), event.couponTemplateId(), count.getIssuedCount());

            } catch (Exception e) {
                log.error("[CouponIssue] failed record={} cause={}", record, e.getMessage());
            }
        }
        ack.acknowledge();
    }
}
