package com.loopers.infrastructure.product;

import com.loopers.domain.product.ViewDedupRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class ViewDedupRedisRepository implements ViewDedupRepository {

    private static final String KEY_PREFIX = "view:bitmap:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public ViewDedupRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean markIfFirstView(Long productDbId, Long memberId, LocalDate date) {
        String key = KEY_PREFIX + productDbId + ":" + date.format(FORMATTER);
        Boolean previous = redisTemplate.opsForValue().setBit(key, memberId, true);
        redisTemplate.expire(key, TTL);
        return Boolean.FALSE.equals(previous);
    }
}
