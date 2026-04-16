package com.loopers.application.ranking;

import com.loopers.event.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 배치 처리를 위한 랭킹 점수 프로세서.
 * 같은 productId에 대한 점수를 애플리케이션에서 먼저 합산한 후
 * Redis에 한 번에 ZINCRBY하��� Redis 연산 횟수를 절감합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingBatchProcessor {

    private final RedisTemplate<String, String> redisTemplate;
    private final RankingScoreCalculator calculator;
    private final RankingKeyGenerator keyGenerator;

    /**
     * 배치로 점수를 추가합니다.
     * 같은 productId에 대한 점수를 합산한 후 Redis에 한 번에 ZINCRBY합니다.
     *
     * @param scoreEntries 점수 엔트리 리스트
     */
    public void addScoresBatch(List<ScoreEntry> scoreEntries) {
        addScoresBatch(scoreEntries, LocalDate.now());
    }

    public void addScoresBatch(List<ScoreEntry> scoreEntries, LocalDate date) {
        if (scoreEntries == null || scoreEntries.isEmpty()) {
            return;
        }

        // 1. productId별 점수 합산
        Map<Long, Double> aggregatedScores = aggregateScores(scoreEntries);

        if (aggregatedScores.isEmpty()) {
            log.debug("No scores to update after aggregation");
            return;
        }

        // 2. Daily 키에 ZINCRBY
        String dailyKey = keyGenerator.generateDailyKey(date);
        boolean isDailyNewKey = Boolean.FALSE.equals(redisTemplate.hasKey(dailyKey));

        // 3. Hourly 키에 ZINCRBY
        String hourlyKey = keyGenerator.generateCurrentHourKey();
        boolean isHourlyNewKey = Boolean.FALSE.equals(redisTemplate.hasKey(hourlyKey));

        for (Map.Entry<Long, Double> entry : aggregatedScores.entrySet()) {
            String member = String.valueOf(entry.getKey());
            double score = entry.getValue();
            // Daily와 Hourly 키에 모두 점수 추가
            redisTemplate.opsForZSet().incrementScore(dailyKey, member, score);
            redisTemplate.opsForZSet().incrementScore(hourlyKey, member, score);
        }

        // 4. 새 키인 경우에만 TTL 설정
        if (isDailyNewKey) {
            redisTemplate.expire(dailyKey, keyGenerator.getTtlDays(), TimeUnit.DAYS);
            log.debug("TTL set for new daily ranking key: key={}, ttlDays={}", dailyKey, keyGenerator.getTtlDays());
        }
        if (isHourlyNewKey) {
            redisTemplate.expire(hourlyKey, keyGenerator.getHourlyTtlHours(), TimeUnit.HOURS);
            log.debug("TTL set for new hourly ranking key: key={}, ttlHours={}", hourlyKey, keyGenerator.getHourlyTtlHours());
        }

        log.debug("Batch ranking scores updated: dailyKey={}, hourlyKey={}, productCount={}, totalEntries={}",
            dailyKey, hourlyKey, aggregatedScores.size(), scoreEntries.size());
    }

    /**
     * productId별로 점수를 합산합니다.
     */
    private Map<Long, Double> aggregateScores(List<ScoreEntry> scoreEntries) {
        Map<Long, Double> aggregated = new HashMap<>();

        for (ScoreEntry entry : scoreEntries) {
            double score = calculator.calculate(entry.eventType(), entry.eventValue());
            if (score != 0.0) {
                aggregated.merge(entry.productId(), score, Double::sum);
            }
        }

        return aggregated;
    }

    /**
     * 점수 엔트리 레코드
     */
    public record ScoreEntry(
        Long productId,
        EventType eventType,
        Double eventValue
    ) {
        public static ScoreEntry of(Long productId, EventType eventType, Double eventValue) {
            return new ScoreEntry(productId, eventType, eventValue);
        }
    }
}
