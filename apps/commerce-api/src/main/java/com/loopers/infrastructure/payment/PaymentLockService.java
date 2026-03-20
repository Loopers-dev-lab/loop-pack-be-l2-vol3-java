package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * {@link PaymentLock} Redis 구현체.
 * <p>
 * Redis SETNX를 이용하여 동일 주문에 대한 동시 결제 요청을 차단한다.
 * {@code hasActivePayment()} SELECT 기반 check-then-act gap을 보완한다.
 * </p>
 *
 * <h3>Redis 장애 정책</h3>
 * <p>
 * Redis 장애 시 {@link PaymentLockException}을 던진다.
 * 호출부(PaymentFacade)에서 Redis 장애(fallback)와 락 경합(즉시 거부)을 구분하여 처리한다.
 * </p>
 */
@Slf4j
@Component
public class PaymentLockService implements PaymentLock {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "payment:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /**
     * Owner-verified unlock Lua script.
     * GET → 값이 lockContext와 일치할 때만 DEL → 원자적 실행.
     * 다른 요청이 획득한 락을 오삭제하는 것을 방지한다.
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    public PaymentLockService(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(Long orderId, String lockContext) {
        String key = LOCK_PREFIX + orderId;
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, lockContext, LOCK_TTL);
            boolean result = Boolean.TRUE.equals(acquired);
            if (result) {
                log.debug("결제 분산락 획득 성공. orderId={}, context={}", orderId, lockContext);
            } else {
                log.info("결제 분산락 획득 실패 (이미 진행 중). orderId={}", orderId);
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis 분산락 획득 중 오류. orderId={}", orderId, e);
            throw new PaymentLockException(e);
        }
    }

    @Override
    public void unlock(Long orderId, String lockContext) {
        String key = LOCK_PREFIX + orderId;
        try {
            Long result = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), lockContext);
            if (Long.valueOf(1L).equals(result)) {
                log.debug("결제 분산락 해제 (owner 검증 통과). orderId={}", orderId);
            } else {
                log.info("결제 분산락 해제 스킵 — owner 불일치 또는 TTL 만료. orderId={}, context={}",
                        orderId, lockContext);
            }
        } catch (Exception e) {
            log.warn("Redis 분산락 해제 실패 — TTL로 자동 만료 예정. orderId={}", orderId, e);
        }
    }
}
