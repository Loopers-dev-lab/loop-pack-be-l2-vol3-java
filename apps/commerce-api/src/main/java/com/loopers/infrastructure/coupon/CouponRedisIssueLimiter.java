package com.loopers.infrastructure.coupon;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.coupon.CouponIssueLimiter;
import com.loopers.domain.coupon.CouponIssueResult;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class CouponRedisIssueLimiter implements CouponIssueLimiter {

    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";
    private static final String QUANTITY_KEY_PREFIX = "coupon:totalQuantity:";
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<String> issueScript;

    public CouponRedisIssueLimiter(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.issueScript = new DefaultRedisScript<>();
        this.issueScript.setLocation(new ClassPathResource("scripts/coupon-issue.lua"));
        this.issueScript.setResultType(String.class);
    }

    @Override
    public CouponIssueResult tryIssue(Long couponId, Long userId) {
        String result = redisTemplate.execute(
                issueScript,
                List.of(ISSUED_KEY_PREFIX + couponId, QUANTITY_KEY_PREFIX + couponId),
                String.valueOf(userId),
                String.valueOf(System.currentTimeMillis()));
        return CouponIssueResult.valueOf(result);
    }

    @Override
    public void rollback(Long couponId, Long userId) {
        redisTemplate.opsForZSet().remove(
                ISSUED_KEY_PREFIX + couponId, String.valueOf(userId));
    }

    @Override
    public void registerTotalQuantity(Long couponId, int totalQuantity) {
        redisTemplate.delete(ISSUED_KEY_PREFIX + couponId);
        redisTemplate.opsForValue().set(
                QUANTITY_KEY_PREFIX + couponId, String.valueOf(totalQuantity));
    }
}
