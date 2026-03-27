package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStockRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Redis 기반 쿠폰 재고 관리 구현체.
 * Lua 스크립트를 통해 중복 체크 + 재고 차감을 원자적으로 처리한다.
 * Master 전용 RedisTemplate 사용 (읽기/쓰기 모두 master).
 */
@Repository
public class RedisCouponStockRepository implements CouponStockRepository {

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String LOCK_KEY_PREFIX = "coupon:issue-lock:";
    private static final long LOCK_TTL_SECONDS = 300; // 5분 — Consumer 처리 시간 + 여유

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> issueRequestScript;

    public RedisCouponStockRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.issueRequestScript = RedisScript.of(
                new ClassPathResource("scripts/coupon_issue_request.lua"), Long.class);
    }

    @Override
    public long tryIssueRequest(Long couponTemplateId, Long userId) {
        String lockKey = LOCK_KEY_PREFIX + couponTemplateId + ":" + userId;
        String stockKey = STOCK_KEY_PREFIX + couponTemplateId;

        Long result = redisTemplate.execute(
                issueRequestScript,
                List.of(lockKey, stockKey),
                String.valueOf(LOCK_TTL_SECONDS));

        return result != null ? result : -1;
    }

    @Override
    public void setStock(Long couponTemplateId, int stock) {
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + couponTemplateId, String.valueOf(stock));
    }
}
