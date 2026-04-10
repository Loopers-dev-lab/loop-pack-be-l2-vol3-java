package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.outbox.EventHandled;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.outbox.EventHandledJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final EventHandledJpaRepository eventHandledRepository;
    private final CouponJpaRepository couponRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestRepository;
    private final IssuedCouponJpaRepository issuedCouponRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.COUPON_ISSUE_REQUESTS,
        groupId = "commerce-streamer-coupon",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("[CouponConsumer] 처리 실패: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) throws Exception {
        String rawPayload = new String((byte[]) record.value());
        JsonNode node = objectMapper.readTree(rawPayload);

        String eventId = node.get("eventId").asText();

        // 멱등 처리: 이미 처리된 이벤트면 skip
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("[CouponConsumer] 중복 이벤트 skip: eventId={}", eventId);
            return;
        }

        JsonNode data = node.get("data");
        Long requestId = data.get("requestId").asLong();
        Long couponId = data.get("couponId").asLong();
        Long userId = data.get("userId").asLong();

        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalStateException("CouponIssueRequest not found: " + requestId));

        Coupon coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow(() -> new IllegalStateException("Coupon not found: " + couponId));

        // 중복 발급 여부 확인
        if (issuedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            log.warn("[CouponConsumer] 이미 발급된 사용자: couponId={}, userId={}", couponId, userId);
            request.markFailed();
            eventHandledRepository.save(new EventHandled(eventId));
            return;
        }

        // 수량 확인
        long issuedCount = issuedCouponRepository.countByCouponId(couponId);
        if (issuedCount >= coupon.getTotalQuantity()) {
            log.info("[CouponConsumer] 쿠폰 소진: couponId={}, issuedCount={}, totalQuantity={}",
                couponId, issuedCount, coupon.getTotalQuantity());
            request.markFailed();
            eventHandledRepository.save(new EventHandled(eventId));
            return;
        }

        // 발급 처리
        issuedCouponRepository.save(new IssuedCoupon(couponId, userId));
        request.markSuccess();
        eventHandledRepository.save(new EventHandled(eventId));

        log.info("[CouponConsumer] 쿠폰 발급 완료: requestId={}, couponId={}, userId={}", requestId, couponId, userId);
    }
}
