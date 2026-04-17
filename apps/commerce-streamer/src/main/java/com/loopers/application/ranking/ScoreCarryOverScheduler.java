package com.loopers.application.ranking;

import com.loopers.config.redis.RankingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 콜드 스타트 완화를 위한 Score Carry-Over 스케줄러.
 *
 * 매일 23:50에 오늘 일간 랭킹 ZSET의 점수를 10%로 할인하여 내일 키에 이월한다.
 * ZUNIONSTORE를 사용하므로 내일 ZSET에 기존 점수가 있어도 안전하게 누적된다.
 *
 * 이월 공식: tomorrow[productId] += today[productId] × 0.1
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ScoreCarryOverScheduler {

    private static final double CARRY_OVER_RATIO = 0.1;

    private final RedisTemplate<String, String> masterRedisTemplate;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        try {
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            String todayKey = RankingKeys.dailyKey(today);
            String tomorrowKey = RankingKeys.dailyKey(tomorrow);

            Long todaySize = masterRedisTemplate.opsForZSet().size(todayKey);
            if (todaySize == null || todaySize == 0L) {
                log.info("[ScoreCarryOverScheduler] 오늘 랭킹 ZSET이 비어있어 이월을 생략합니다.");
                return;
            }

            // ZUNIONSTORE tomorrowKey 2 todayKey tomorrowKey WEIGHTS 0.1 1.0
            // → tomorrowKey = todayKey × 0.1 + tomorrowKey × 1.0
            masterRedisTemplate.opsForZSet().unionAndStore(
                    todayKey,
                    List.of(tomorrowKey),
                    tomorrowKey,
                    Aggregate.SUM,
                    Weights.of(CARRY_OVER_RATIO, 1.0)
            );
            masterRedisTemplate.expire(tomorrowKey, RankingKeys.dailyTtl(tomorrow));

            log.info("[ScoreCarryOverScheduler] carry-over 완료: {} → {} (비율={})", todayKey, tomorrowKey, CARRY_OVER_RATIO);
        } catch (Exception e) {
            log.error("[ScoreCarryOverScheduler] carry-over 오류", e);
        }
    }
}
