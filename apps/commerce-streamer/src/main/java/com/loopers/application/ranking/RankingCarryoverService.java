package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingTtlPolicy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 전일 랭킹 일부를 당일 키로 이월(carryover)한다.
 * <p>
 * 전일 Top N을 {@code weight}로 감쇠해 당일 키에 기록한다. 이후 실시간/보정 경로의 매트릭 기반 ZADD가
 * 동일 member에 대해 점수를 덮어쓰므로, 일시적 불일치가 있어도 최종적으로 DB 원장 기준 점수로 수렴한다.
 */
@Service
public class RankingCarryoverService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RankingTtlPolicy ttlPolicy = new RankingTtlPolicy();
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public RankingCarryoverService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 자정 직후 전일 랭킹을 당일 키로 이월한다.
     * @param props 설정
     */
    public void carryoverToday(RankingCarryoverProperties props) {
        ZoneId zone = ZoneId.of(props.zone());
        LocalDate today = LocalDate.now(zone);
        carryover(today, props);
    }

    void carryover(LocalDate today, RankingCarryoverProperties props) {
        if (today == null || props == null) {
            return;
        }
        if (!props.enabled()) {
            return;
        }
        if (props.topN() <= 0) {
            return;
        }
        if (!(props.weight() > 0.0d && props.weight() < 1.0d)) {
            return;
        }

        String todayKey = "ranking:all:" + DATE.format(today);
        if (props.onlyWhenTodayEmpty()) {
            Long todaySize = redisTemplate.opsForZSet().size(todayKey);
            if (todaySize != null && todaySize > 0L) {
                return;
            }
        }

        // 전일 키는 resolver를 그대로 쓰되, LocalDate -> Instant 변환 없이 format으로 동일 계약을 유지한다.
        LocalDate yesterday = today.minusDays(1);
        String yesterdayKey = "ranking:all:" + DATE.format(yesterday);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(yesterdayKey, 0, props.topN() - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return;
        }

        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t == null) {
                continue;
            }
            String member = t.getValue();
            Double score = t.getScore();
            if (member == null || score == null) {
                continue;
            }
            double seeded = score * props.weight();
            if (seeded <= 0.0d) {
                continue;
            }
            redisTemplate.opsForZSet().add(todayKey, member, seeded);
        }

        ensureTtl(todayKey, ttlPolicy.dailyKeyTtl());
    }

    /**
     * 당일 키의 TTL을 보장한다.
     * @param key 당일 키
     * @param ttl TTL
     */
    private void ensureTtl(String key, Duration ttl) {
        if (key == null || ttl == null) {
            return;
        }
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds < 0L) {
            redisTemplate.expire(key, ttl);
        }
    }
}

