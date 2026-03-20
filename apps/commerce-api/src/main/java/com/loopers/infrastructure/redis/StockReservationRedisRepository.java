package com.loopers.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 재고 예약 Redis 저장소.
 *
 * <p>가주문 시 Redis DECR로 재고 선점, 결제 실패/취소 시 INCR로 복원.
 * 결제 완료 시 DB 재고 차감 + Redis DEL.</p>
 *
 * <p>Redis-DB 재고 정합성은 Phase 3에서 Lua Script v2 배치로 보정.</p>
 *
 * @see <a href="06-resilience-review.md §16.3">Option C: Redis Reservation + DB Confirmation</a>
 */
@Slf4j
@Component
public class StockReservationRedisRepository {

    private static final String KEY_PREFIX = "stock:";

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;

    public StockReservationRedisRepository(
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
    }

    /**
     * 재고 감소 (예약). 음수 방지는 호출자 책임.
     *
     * @return 감소 후 재고량
     */
    public Long decrease(Long productId, int quantity) {
        String key = KEY_PREFIX + productId;
        Long result = writeTemplate.opsForValue().decrement(key, quantity);
        log.debug("재고 예약: productId={}, quantity={}, remaining={}", productId, quantity, result);
        return result;
    }

    /**
     * 재고 복원 (결제 실패/취소).
     *
     * @return 복원 후 재고량
     */
    public Long increase(Long productId, int quantity) {
        String key = KEY_PREFIX + productId;
        Long result = writeTemplate.opsForValue().increment(key, quantity);
        log.debug("재고 복원: productId={}, quantity={}, remaining={}", productId, quantity, result);
        return result;
    }

    /**
     * 현재 재고량 조회.
     */
    public Long getStock(Long productId) {
        String key = KEY_PREFIX + productId;
        String value = readTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : null;
    }

    /**
     * 재고 초기화 (DB 동기화용).
     */
    public void setStock(Long productId, long quantity) {
        String key = KEY_PREFIX + productId;
        writeTemplate.opsForValue().set(key, String.valueOf(quantity));
    }
}
