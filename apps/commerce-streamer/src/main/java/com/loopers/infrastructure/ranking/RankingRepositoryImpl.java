package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RequiredArgsConstructor
@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_SECONDS = 2 * 24 * 60 * 60L;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void flush(Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct) {
        if (deltaByDateAndProduct.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            deltaByDateAndProduct.forEach((date, productDeltas) -> {
                String key = KEY_PREFIX + date.format(DATE_FORMAT);
                productDeltas.forEach((productId, delta) ->
                    conn.zIncrBy(key, delta, productId.toString())
                );
                conn.expire(key, TTL_SECONDS);
            });
            return null;
        });
    }
}
