package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.coupon.CouponEntity;
import com.loopers.domain.coupon.CouponIssueEntity;
import com.loopers.domain.idempotency.EventHandled;
import com.loopers.infrastructure.coupon.CouponIssueJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final CouponJpaRepository couponRepository;
    private final CouponIssueJpaRepository couponIssueRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "coupon-issue-requests",
        groupId = "streamer-coupon",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("coupon-issue-requests 처리 실패: offset={}, error={}", record.offset(), e.getMessage(), e);
            }
        }
        ack.acknowledge();
    }

    @Transactional
    public void processRecord(ConsumerRecord<String, String> record) {
        String eventId = getHeader(record, "X-Event-Id");
        if (eventId == null) {
            log.warn("이벤트 헤더 누락: offset={}", record.offset());
            return;
        }

        // 멱등 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        JsonNode envelope = parsePayload(record.value());
        if (envelope == null) return;

        JsonNode data = envelope.get("data");
        Long couponId = data.get("couponId").asLong();
        Long userId = data.get("userId").asLong();

        // 비관적 락으로 쿠폰 조회
        CouponEntity coupon = couponRepository.findByIdForUpdate(couponId).orElse(null);
        if (coupon == null) {
            log.warn("쿠폰 없음: couponId={}", couponId);
            eventHandledRepository.save(new EventHandled(eventId, "COUPON_ISSUE_REQUESTED"));
            return;
        }

        // 만료 체크
        if (coupon.isExpired()) {
            log.info("만료된 쿠폰 발급 거부: couponId={}, userId={}", couponId, userId);
            eventHandledRepository.save(new EventHandled(eventId, "COUPON_ISSUE_REQUESTED"));
            return;
        }

        // 수량 체크
        if (!coupon.isQuantityAvailable()) {
            log.info("쿠폰 수량 초과: couponId={}, userId={}", couponId, userId);
            eventHandledRepository.save(new EventHandled(eventId, "COUPON_ISSUE_REQUESTED"));
            return;
        }

        // 중복 발급 체크
        if (couponIssueRepository.findByUserIdAndCouponId(userId, couponId).isPresent()) {
            log.info("이미 발급된 쿠폰: couponId={}, userId={}", couponId, userId);
            eventHandledRepository.save(new EventHandled(eventId, "COUPON_ISSUE_REQUESTED"));
            return;
        }

        // 발급 처리
        try {
            coupon.incrementIssuedCount();
            couponRepository.save(coupon);

            CouponIssueEntity issue = new CouponIssueEntity(couponId, userId, coupon.getExpiredAt());
            couponIssueRepository.save(issue);

            eventHandledRepository.save(new EventHandled(eventId, "COUPON_ISSUE_REQUESTED"));
            log.info("쿠폰 발급 성공: couponId={}, userId={}", couponId, userId);
        } catch (DataIntegrityViolationException e) {
            log.info("쿠폰 중복 발급 방지 (UK): couponId={}, userId={}", couponId, userId);
            eventHandledRepository.save(new EventHandled(eventId, "COUPON_ISSUE_REQUESTED"));
        }
    }

    private String getHeader(ConsumerRecord<?, ?> record, String key) {
        var header = record.headers().lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("payload 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
