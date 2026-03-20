package com.loopers.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 가주문(Provisional Order) Redis 저장소.
 *
 * <p>주문서 작성 → Redis HSET(가주문) → 결제 진행.
 * 결제 완료 시 DB INSERT(진주문) + Redis DEL(가주문).</p>
 *
 * <p>TTL: 30분 ±5분 Jitter (25~35분) — 동시 만료에 의한 Redis 부하 방지.</p>
 *
 * @see <a href="06-resilience-review.md §16.14.4">TTL Jitter 설계</a>
 */
@Slf4j
@Component
public class ProvisionalOrderRedisRepository {

    private static final String KEY_PREFIX = "provisional-order:";
    private static final long BASE_TTL_MINUTES = 30;
    private static final long JITTER_MINUTES = 5;

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public ProvisionalOrderRedisRepository(
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
        ObjectMapper objectMapper
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(Long orderId, Map<String, Object> orderData) {
        try {
            String key = KEY_PREFIX + orderId;
            String json = objectMapper.writeValueAsString(orderData);
            long ttl = calculateTtlWithJitter();
            writeTemplate.opsForValue().set(key, json, ttl, TimeUnit.MINUTES);
            log.debug("가주문 저장: orderId={}, ttl={}분", orderId, ttl);
        } catch (Exception e) {
            log.error("가주문 Redis 저장 실패: orderId={}", orderId, e);
            throw new RuntimeException("가주문 저장 실패", e);
        }
    }

    public Optional<Map<String, Object>> findByOrderId(Long orderId) {
        try {
            String key = KEY_PREFIX + orderId;
            String json = readTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            return Optional.of(data);
        } catch (Exception e) {
            log.warn("가주문 Redis 조회 실패: orderId={}", orderId, e);
            return Optional.empty();
        }
    }

    public void deleteByOrderId(Long orderId) {
        try {
            String key = KEY_PREFIX + orderId;
            writeTemplate.delete(key);
            log.debug("가주문 삭제: orderId={}", orderId);
        } catch (Exception e) {
            log.warn("가주문 Redis 삭제 실패: orderId={}", orderId, e);
        }
    }

    public boolean exists(Long orderId) {
        try {
            String key = KEY_PREFIX + orderId;
            return Boolean.TRUE.equals(readTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("가주문 Redis 존재 확인 실패: orderId={}", orderId, e);
            return false;
        }
    }

    /**
     * 모든 가주문 orderId 목록을 반환한다.
     * ProvisionalOrderExpiryScheduler가 TTL 만료 선제 정리에 사용.
     */
    public Set<Long> getAllOrderIds() {
        try {
            Set<Long> orderIds = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*")
                .count(100)
                .build();
            try (Cursor<String> cursor = readTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    orderIds.add(Long.parseLong(key.substring(KEY_PREFIX.length())));
                }
            }
            return orderIds;
        } catch (Exception e) {
            log.warn("가주문 목록 조회 실패", e);
            return Collections.emptySet();
        }
    }

    /**
     * 가주문의 남은 TTL(초)을 반환한다.
     *
     * @return TTL 초, 키가 없으면 -2, TTL 없으면 -1
     */
    public long getTtlSeconds(Long orderId) {
        try {
            String key = KEY_PREFIX + orderId;
            Long ttl = readTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null ? ttl : -2;
        } catch (Exception e) {
            log.warn("가주문 TTL 조회 실패: orderId={}", orderId, e);
            return -2;
        }
    }

    /**
     * TTL Jitter: 30분 ±5분 (25~35분).
     * 동시 만료에 의한 Redis Thundering Herd 방지.
     */
    long calculateTtlWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(-JITTER_MINUTES, JITTER_MINUTES + 1);
        return BASE_TTL_MINUTES + jitter;
    }
}
