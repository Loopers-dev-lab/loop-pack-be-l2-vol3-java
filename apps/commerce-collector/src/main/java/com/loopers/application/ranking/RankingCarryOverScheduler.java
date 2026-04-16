package com.loopers.application.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RedisTemplate<String, String> redisTemplate;
    private final RankingKeyGenerator keyGenerator;

    /**
     * 매일 23:50에 실행되어 오늘 점수의 10%를 내일 키에 이월합니다.
     * 콜드 스타트 완화를 위한 스케줄러입니다.
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverScores() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        String todayKey = keyGenerator.generateDailyKey(today);
        String tomorrowKey = keyGenerator.generateDailyKey(tomorrow);

        // 오늘 데이터 조회
        Set<TypedTuple<String>> todayScores = redisTemplate.opsForZSet()
            .rangeWithScores(todayKey, 0, -1);

        if (todayScores == null || todayScores.isEmpty()) {
            log.debug("No data to carry over for date={}", today);
            return;
        }

        // 오늘 점수의 10%를 내일 키에 추가 (ZINCRBY)
        for (TypedTuple<String> tuple : todayScores) {
            String productId = tuple.getValue();
            Double score = tuple.getScore();
            if (productId != null && score != null) {
                double carryOverScore = score * CARRY_OVER_WEIGHT;
                redisTemplate.opsForZSet().incrementScore(tomorrowKey, productId, carryOverScore);
            }
        }

        // TTL 설정
        int ttlDays = keyGenerator.getTtlDays();
        redisTemplate.expire(tomorrowKey, ttlDays, TimeUnit.DAYS);

        log.info("Carried over scores: from={} to={}, count={}, weight={}",
            todayKey, tomorrowKey, todayScores.size(), CARRY_OVER_WEIGHT);
    }
}