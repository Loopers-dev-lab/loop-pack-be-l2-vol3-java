package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class RedisRankingRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ZINCRBY 후 TTL이 없는 경우에만 EXPIREAT 설정 (1 RTT, 원자적)
    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = RedisScript.of(
            "redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2])\n" +
            "if redis.call('TTL', KEYS[1]) < 0 then\n" +
            "  redis.call('EXPIREAT', KEYS[1], ARGV[3])\n" +
            "end\n" +
            "return 1",
            Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void incrementScore(Long productId, LocalDate date, double increment) {
        String key = KEY_PREFIX + date.format(DATE_FORMATTER);
        Instant expireAt = date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();

        redisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                String.valueOf(increment),
                productId.toString(),
                String.valueOf(expireAt.getEpochSecond())
        );
    }
}
