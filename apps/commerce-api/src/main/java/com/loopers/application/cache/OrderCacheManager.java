package com.loopers.application.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

/**
 * 주문 목록 캐시 매니저
 *
 * Cache-Aside 패턴의 캐시 읽기/저장/무효화를 담당한다.
 * 무효화 전략: afterCommit DELETE + TTL 안전망 (Double Delete 안 함)
 *
 * 키 설계:
 *   주문 목록: orders:list:{userId}
 *   - 첫 페이지(cursor=null) + 기본 조회(최근 3개월)만 캐싱
 *   - 커스텀 기간 조회는 캐시 우회
 */
@Component
public class OrderCacheManager {

    private static final Logger log = LoggerFactory.getLogger(OrderCacheManager.class);

    private static final String LIST_KEY_PREFIX = "orders:list:";
    private static final Duration LIST_TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderCacheManager(RedisTemplate<String, String> redisTemplate,
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ── 주문 목록 캐시 ──

    public Optional<OrderFacade.OrderCursorResult> getOrderList(Long userId) {
        String key = LIST_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OrderFacade.OrderCursorResult.class));
        } catch (JsonProcessingException e) {
            log.warn("주문 목록 캐시 역직렬화 실패 (userId={}), 캐시 삭제 후 DB 조회", userId, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void putOrderList(Long userId, OrderFacade.OrderCursorResult result) {
        String key = LIST_KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, LIST_TTL);
        } catch (JsonProcessingException e) {
            log.warn("주문 목록 캐시 직렬화 실패 (userId={})", userId, e);
        }
    }

    /**
     * @Transactional 메서드에서 호출 — afterCommit에서 캐시 삭제.
     */
    public void registerEvictAfterCommit(Long userId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictOrderList(userId);
                    }
                }
        );
    }

    /**
     * txTemplate 등 프로그래밍 방식 트랜잭션에서 커밋 후 직접 호출.
     */
    public void evictOrderList(Long userId) {
        redisTemplate.delete(LIST_KEY_PREFIX + userId);
    }
}
