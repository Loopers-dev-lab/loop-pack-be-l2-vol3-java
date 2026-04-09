package com.loopers.application;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisZSetCommands.Aggregate;
import org.springframework.data.redis.connection.RedisZSetCommands.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 매일 23:50 — 오늘 랭킹의 10%를 내일 키에 carry-over.
 * ZUNIONSTORE dest 2 tomorrowKey todayKey WEIGHTS 1 0.1
 * → 내일 키에 오늘 점수 × 0.1 합산. 자정 전 실행이므로
 *   내일 키가 없으면 새로 생성, 이미 있으면 기존 점수에 합산.
 */
@Slf4j
@Component
public class RankingCarryOverScheduler {

    private static final DateTimeFormatter KEY_DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long TTL_DAYS = 2;

    private final RedisTemplate<String, String> redisTemplate;

    public RankingCarryOverScheduler(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        String todayKey = rankingKey(today);
        String tomorrowKey = rankingKey(today.plusDays(1));

        Boolean todayExists = redisTemplate.hasKey(todayKey);
        if (todayExists == null || !todayExists) {
            log.info("[RankingCarryOver] todayKey={} 없음 — skip", todayKey);
            return;
        }

        // ZUNIONSTORE tomorrowKey 2 tomorrowKey todayKey WEIGHTS 1 0.1 AGGREGATE SUM
        redisTemplate.opsForZSet().unionAndStore(
            tomorrowKey,
            List.of(todayKey),
            tomorrowKey,
            Aggregate.SUM,
            Weights.of(1, 0.1)
        );

        // 내일 키 TTL 설정 (아직 미설정인 경우)
        Long ttl = redisTemplate.getExpire(tomorrowKey);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(tomorrowKey, TTL_DAYS, TimeUnit.DAYS);
        }

        log.info("[RankingCarryOver] {} × 0.1 → {} 완료", todayKey, tomorrowKey);
    }

    private String rankingKey(LocalDate date) {
        return "ranking:all:" + date.format(KEY_DATE_FMT);
    }
}
