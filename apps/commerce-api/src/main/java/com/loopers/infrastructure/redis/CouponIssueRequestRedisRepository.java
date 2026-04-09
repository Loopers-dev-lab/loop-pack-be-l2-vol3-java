package com.loopers.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestInfo;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CouponIssueRequestRedisRepository {

    private static final String KEY_PREFIX = "coupon:request:";
    private static final String SEQ_KEY = "coupon:request:seq";
    private static final long TTL_SECONDS = 600; // 10분

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public CouponIssueRequestRedisRepository(
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
        ObjectMapper objectMapper
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.objectMapper = objectMapper;
    }

    public Long nextId() {
        return writeTemplate.opsForValue().increment(SEQ_KEY);
    }

    public void save(Long requestId, Long couponId, Long memberId, String status, String rejectReason) {
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
            log.debug("쿠폰 발급 요청 저장: requestId={}, status={}", requestId, status);
        } catch (Exception e) {
            log.error("쿠폰 발급 요청 Redis 저장 실패: requestId={}", requestId, e);
            throw new RuntimeException("쿠폰 발급 요청 저장 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<CouponIssueRequestInfo> findById(Long requestId) {
        try {
            String key = KEY_PREFIX + requestId;
            String json = readTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            return Optional.of(new CouponIssueRequestInfo(
                ((Number) data.get("requestId")).longValue(),
                ((Number) data.get("couponId")).longValue(),
                ((Number) data.get("memberId")).longValue(),
                CouponIssueRequestStatus.valueOf((String) data.get("status")),
                data.get("rejectReason") != null && !((String) data.get("rejectReason")).isEmpty()
                    ? (String) data.get("rejectReason") : null
            ));
        } catch (Exception e) {
            log.warn("쿠폰 발급 요청 Redis 조회 실패: requestId={}", requestId, e);
            return Optional.empty();
        }
    }
}
