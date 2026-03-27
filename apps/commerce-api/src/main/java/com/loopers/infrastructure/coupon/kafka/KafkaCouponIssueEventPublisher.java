package com.loopers.infrastructure.coupon.kafka;

import java.time.LocalDateTime;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueEventPublisher;
import com.loopers.domain.coupon.CouponStockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka 기반 쿠폰 발급 이벤트 발행 구현체.
 *
 * <p>비동기로 메시지를 발행하며, 발행 실패 시 Redis 재고를 롤백한다.
 * 파티션 키로 couponId를 사용하여 같은 쿠폰의 이벤트 순서를 보장한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCouponIssueEventPublisher implements CouponIssueEventPublisher {

    private static final String TOPIC = "coupon-issue-v1";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final CouponStockManager couponStockManager;
    private final ObjectMapper objectMapper;

    @Override
    public void publishEvent(Long couponId, Long userId) {
        CouponIssuedPayload payload = new CouponIssuedPayload(couponId, userId, LocalDateTime.now());
        String partitionKey = String.valueOf(couponId);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[CouponIssuedEvent] 발행 실패. couponId={}, userId={}", couponId, userId, ex);
                            couponStockManager.rollback(couponId, userId);
                            return;
                        }
                        log.debug("[CouponIssuedEvent] 발행 성공. couponId={}, userId={}, offset={}",
                                couponId, userId, result.getRecordMetadata().offset());
                    });
        } catch (JsonProcessingException e) {
            log.error("[CouponIssuedEvent] 직렬화 실패. couponId={}, userId={}", couponId, userId, e);
            couponStockManager.rollback(couponId, userId);
        }
    }
}
