package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 콜드 스타트 완화를 위한 Score Carry-Over 스케줄러.
 * 매일 23:50에 오늘 랭킹 점수의 10%를 내일 키에 미리 복사한다.
 * ShedLock으로 다중 인스턴스 환경에서 중복 실행을 방지한다.
 */
@Slf4j
@Component
public class RankingScheduler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "ranking:all:";
    private static final Duration TTL = Duration.ofDays(2);
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RankingScheduler(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "rankingCarryOver", lockAtMostFor = "5m", lockAtLeastFor = "1m")
    public void carryOverScore() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        String todayKey = KEY_PREFIX + today.format(DATE_FORMATTER);
        String tomorrowKey = KEY_PREFIX + tomorrow.format(DATE_FORMATTER);

        log.info("랭킹 Carry-Over 시작: {} → {} (weight={})", todayKey, tomorrowKey, CARRY_OVER_WEIGHT);

        try {
            masterRedisTemplate.execute((RedisConnection connection) -> {
                byte[] src = todayKey.getBytes(StandardCharsets.UTF_8);
                byte[] dst = tomorrowKey.getBytes(StandardCharsets.UTF_8);
                // ZUNIONSTORE dstKey 1 srcKey WEIGHTS 0.1
                connection.zSetCommands().zUnionStore(dst, Aggregate.SUM, Weights.of(CARRY_OVER_WEIGHT), src);
                connection.keyCommands().expire(dst, TTL.getSeconds());
                return null;
            });
            log.info("랭킹 Carry-Over 완료: {}", tomorrowKey);
        } catch (Exception e) {
            log.error("랭킹 Carry-Over 실패", e);
        }
    }
}
