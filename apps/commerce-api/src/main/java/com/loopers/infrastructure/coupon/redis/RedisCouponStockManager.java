package com.loopers.infrastructure.coupon.redis;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponStockManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis Lua Script 기반 쿠폰 재고 관리 구현체.
 *
 * <p>Lua Script를 통해 잔여 수량 차감(DECR), 중복 검증(SISMEMBER), 발급 기록(SADD)을
 * 원자적으로 수행한다. 쿠폰 만료일 기준 TTL을 설정하여 만료된 키가 자동 정리된다.</p>
 *
 * <p>Redis 키 구조:</p>
 * <ul>
 *   <li>{@code coupon:{couponId}:stock} — 잔여 발급 수량 (String, totalQuantity에서 차감)</li>
 *   <li>{@code coupon:{couponId}:users} — 발급된 사용자 ID 집합 (Set)</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisCouponStockManager implements CouponStockManager {

    private static final String STOCK_KEY_FORMAT = "coupon:%d:stock";
    private static final String USERS_KEY_FORMAT = "coupon:%d:users";

    /**
     * Lua Script: 잔여 수량을 DECR하여 원자적으로 발급을 처리한다.
     *
     * <p>KEYS[1] = coupon:{id}:stock, KEYS[2] = coupon:{id}:users</p>
     * <p>ARGV[1] = userId</p>
     * <p>반환값: 1=성공, 0=수량 소진, -1=중복 발급, -2=미초기화</p>
     */
    private static final String TRY_ISSUE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -2
            end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                return -1
            end
            local stock = redis.call('DECR', KEYS[1])
            if stock < 0 then
                redis.call('INCR', KEYS[1])
                return 0
            end
            redis.call('SADD', KEYS[2], ARGV[1])
            return 1
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> issueScript;

    public RedisCouponStockManager(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.issueScript = new DefaultRedisScript<>(TRY_ISSUE_SCRIPT, Long.class);
    }

    @Override
    public void initialize(Long couponId, int totalQuantity, ZonedDateTime expiredAt) {
        String stockKey = String.format(STOCK_KEY_FORMAT, couponId);
        String usersKey = String.format(USERS_KEY_FORMAT, couponId);
        Duration ttl = Duration.between(ZonedDateTime.now(), expiredAt);

        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(totalQuantity), ttl);
        redisTemplate.expire(usersKey, ttl);
    }

    @Override
    public CouponIssueStatus issueCoupon(Long couponId, Long userId) {
        String stockKey = String.format(STOCK_KEY_FORMAT, couponId);
        String usersKey = String.format(USERS_KEY_FORMAT, couponId);

        Long result = redisTemplate.execute(
                issueScript,
                List.of(stockKey, usersKey),
                String.valueOf(userId)
        );
        return CouponIssueStatus.fromCode(result.intValue());
    }

    @Override
    public void sync(Long couponId, int stock, Set<Long> userIds, ZonedDateTime expiredAt) {
        String stockKey = String.format(STOCK_KEY_FORMAT, couponId);
        String usersKey = String.format(USERS_KEY_FORMAT, couponId);
        Duration ttl = Duration.between(ZonedDateTime.now(), expiredAt);

        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock), ttl);

        redisTemplate.delete(usersKey);
        if (!userIds.isEmpty()) {
            String[] userIdStrings = userIds.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(usersKey, userIdStrings);
        }
        redisTemplate.expire(usersKey, ttl);

        log.debug("[CouponStockSync] couponId={}, stock={}, users={}", couponId, stock, userIds.size());
    }

    @Override
    public void rollback(Long couponId, Long userId) {
        String stockKey = String.format(STOCK_KEY_FORMAT, couponId);
        String usersKey = String.format(USERS_KEY_FORMAT, couponId);

        redisTemplate.opsForValue().increment(stockKey);
        redisTemplate.opsForSet().remove(usersKey, String.valueOf(userId));

        log.warn("[CouponStockRollback] couponId={}, userId={}", couponId, userId);
    }
}
