package com.loopers.infrastructure.collector;

import com.loopers.application.collector.ProductSoldContributionLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * (partitionKey, productId, 일자)별 당일 누적 판매 수량 상한을 Redis Lua로 원자 적용한다.
 */
@Component
public class RedisProductSoldContributionLimiter implements ProductSoldContributionLimiter {

    private static final String KEY_PREFIX = "collector:sold-cap:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String LUA = """
            local key = KEYS[1]
            local add = tonumber(ARGV[1])
            local cap = tonumber(ARGV[2])
            local ttlSeconds = tonumber(ARGV[3])
            local raw = redis.call('GET', key)
            local current = 0
            if raw then
              current = tonumber(raw)
            end
            if current + add > cap then
              return 0
            end
            redis.call('INCRBY', key, add)
            if current == 0 then
              redis.call('EXPIRE', key, ttlSeconds)
            end
            return 1
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setScriptText(LUA);
        SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final long dailyCapQuantityPerPartitionProduct;
    private final long ttlSeconds;

    public RedisProductSoldContributionLimiter(
            RedisTemplate<String, String> redisTemplate,
            @Value("${collector.product.sold-contribution.daily-cap-quantity-per-partition-product:0}")
            long dailyCapQuantityPerPartitionProduct,
            @Value("${collector.product.sold-contribution.key-ttl-days:2}") long ttlDays) {
        this.redisTemplate = redisTemplate;
        this.dailyCapQuantityPerPartitionProduct = dailyCapQuantityPerPartitionProduct;
        this.ttlSeconds = ttlDays * 86400L;
    }

    @Override
    public boolean allowContribution(String partitionKey, long productId, long quantity, Instant occurredAt) {
        if (dailyCapQuantityPerPartitionProduct <= 0L) {
            return true;
        }
        if (partitionKey == null || partitionKey.isBlank() || productId <= 0L || quantity <= 0L) {
            return true;
        }

        String day = DAY_FORMATTER.format(occurredAt.atZone(KST).toLocalDate());
        String key = KEY_PREFIX + day + ":" + partitionKey + ":" + productId;
        Long ok = redisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                Long.toString(quantity),
                Long.toString(dailyCapQuantityPerPartitionProduct),
                Long.toString(ttlSeconds));
        return ok != null && ok == 1L;
    }
}
