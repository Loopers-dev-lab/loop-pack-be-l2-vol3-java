package com.loopers.infrastructure.collector;

import com.loopers.application.collector.ProductViewContributionLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 유저/세션/디바이스(partitionKey) + 상품 + 일자 단위 조회 기여 상한을 Redis로 제어한다.
 */
@Component
public class RedisProductViewContributionLimiter implements ProductViewContributionLimiter {

    private static final String KEY_PREFIX = "collector:view-cap:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedisTemplate<String, String> redisTemplate;
    private final long dailyCapPerPartitionProduct;
    private final long ttlDays;

    /**
     * 조회 기여 상한을 Redis로 제어한다.
     * @param redisTemplate Redis 템플릿
     * @param dailyCapPerPartitionProduct 일일 상한 수량
     * @param ttlDays 키 TTL 일수
     */
    public RedisProductViewContributionLimiter(
            RedisTemplate<String, String> redisTemplate,
            @Value("${collector.product.view-contribution.daily-cap-per-partition-product:0}")
            long dailyCapPerPartitionProduct,
            @Value("${collector.product.view-contribution.key-ttl-days:2}") long ttlDays) {
        this.redisTemplate = redisTemplate;
        this.dailyCapPerPartitionProduct = dailyCapPerPartitionProduct;
        this.ttlDays = ttlDays;
    }

    /**
     * 조회 기여 상한을 판단한다.
     * @param partitionKey 유저/세션/디바이스를 대표하는 키
     * @param productId 상품 ID
     * @param occurredAt 이벤트 발생 시각
     * @return true면 view_count 기여를 허용, false면 상한 초과로 차단
     */
    @Override
    public boolean allowContribution(String partitionKey, long productId, Instant occurredAt) {
        if (dailyCapPerPartitionProduct <= 0L) {
            return true;
        }
        if (partitionKey == null || partitionKey.isBlank() || productId <= 0L) {
            return true;
        }

        String day = DAY_FORMATTER.format(occurredAt.atZone(KST).toLocalDate());
        String key = KEY_PREFIX + day + ":" + partitionKey + ":" + productId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(ttlDays));
        }
        return count != null && count <= dailyCapPerPartitionProduct;
    }
}
