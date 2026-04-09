package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 선착순 쿠폰 발급 요청 Consumer.
 *
 * <p>coupon-issue-requests 토픽에서 요청을 소비하여 쿠폰을 발급하고,
 * 결과를 Redis에 기록한다 (DB가 아닌 Redis TTL로 요청 추적).</p>
 */
@Slf4j
@Component
public class CouponIssueConsumer {

    private static final String KEY_PREFIX = "coupon:request:";
    private static final long TTL_SECONDS = 600; // 10분

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public CouponIssueConsumer(
        JdbcTemplate jdbcTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.writeTemplate = writeTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "coupon-issue-requests",
        containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    @SuppressWarnings("unchecked")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Long requestId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), Map.class);
            requestId = ((Number) payload.get("requestId")).longValue();
            Long couponId = ((Number) payload.get("couponId")).longValue();
            Long memberId = ((Number) payload.get("memberId")).longValue();

            // 쿠폰 유효성 검증 + 발급
            issueCoupon(couponId, memberId);

            // 성공 → Redis COMPLETED
            updateRequestStatus(requestId, couponId, memberId, "COMPLETED", null);
            log.info("쿠폰 발급 성공: requestId={}, couponId={}, memberId={}", requestId, couponId, memberId);

        } catch (Exception e) {
            log.error("쿠폰 발급 실패: requestId={}, reason={}", requestId, e.getMessage(), e);
            if (requestId != null) {
                try {
                    Map<String, Object> payload = objectMapper.readValue(record.value(), Map.class);
                    Long couponId = ((Number) payload.get("couponId")).longValue();
                    Long memberId = ((Number) payload.get("memberId")).longValue();
                    updateRequestStatus(requestId, couponId, memberId, "REJECTED", e.getMessage());
                } catch (Exception inner) {
                    log.error("Redis 상태 업데이트 실패: requestId={}", requestId, inner);
                }
            }
        } finally {
            ack.acknowledge();
        }
    }

    private void issueCoupon(Long couponId, Long memberId) {
        // 쿠폰 존재 및 만료 확인
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM coupon WHERE id = ? AND expired_at > NOW() AND deleted_at IS NULL",
            Integer.class, couponId
        );
        if (count == null || count == 0) {
            throw new IllegalStateException("쿠폰이 존재하지 않거나 만료되었습니다. couponId=" + couponId);
        }

        // 쿠폰 발급 (coupon_issue INSERT)
        jdbcTemplate.update(
            "INSERT INTO coupon_issue (coupon_id, member_id, status, expired_at, created_at) " +
            "SELECT ?, ?, 'AVAILABLE', expired_at, NOW() FROM coupon WHERE id = ?",
            couponId, memberId, couponId
        );
    }

    private void updateRequestStatus(Long requestId, Long couponId, Long memberId,
                                     String status, String rejectReason) {
        try {
            String key = KEY_PREFIX + requestId;
            Map<String, Object> data = Map.of(
                "requestId", requestId,
                "couponId", couponId,
                "memberId", memberId,
                "status", status,
                "rejectReason", rejectReason != null ? rejectReason : ""
            );
            String json = objectMapper.writeValueAsString(data);
            writeTemplate.opsForValue().set(key, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis 상태 업데이트 실패: requestId={}, status={}", requestId, status, e);
        }
    }
}
