package com.loopers.fake;

import com.loopers.application.ranking.RankingKeyGenerator;
import com.loopers.application.ranking.RankingProperties;
import com.loopers.application.ranking.RankingScoreCalculator;
import com.loopers.application.ranking.RankingService;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * 테스트용 FakeRankingService.
 * Redis 없이 메모리에 점수를 저장합니다.
 */
public class FakeRankingService extends RankingService {

    private final Map<String, Map<Long, Double>> scores = new HashMap<>();
    private final RankingKeyGenerator keyGenerator;
    private final RankingScoreCalculator calculator;

    @SuppressWarnings("unchecked")
    public FakeRankingService() {
        super(mock(RedisTemplate.class), createCalculator(), createKeyGenerator());
        this.keyGenerator = createKeyGenerator();
        this.calculator = createCalculator();
    }

    private static RankingProperties createProperties() {
        return new RankingProperties(0.1, 0.2, 0.7, "ranking:all:", 2);
    }

    private static RankingScoreCalculator createCalculator() {
        return new RankingScoreCalculator(createProperties());
    }

    private static RankingKeyGenerator createKeyGenerator() {
        return new RankingKeyGenerator(createProperties());
    }

    @Override
    public void addScore(Long productId, com.loopers.event.EventType eventType, Double eventValue) {
        addScore(productId, eventType, eventValue, LocalDate.now());
    }

    @Override
    public void addScore(Long productId, com.loopers.event.EventType eventType, Double eventValue, LocalDate date) {
        double score = calculator.calculate(eventType, eventValue);
        if (score == 0.0) {
            return;
        }

        String key = keyGenerator.generateDailyKey(date);
        scores.computeIfAbsent(key, k -> new HashMap<>())
              .merge(productId, score, Double::sum);
    }

    public Double getScore(String key, Long productId) {
        Map<Long, Double> keyScores = scores.get(key);
        return keyScores != null ? keyScores.get(productId) : null;
    }

    public Map<String, Map<Long, Double>> getAllScores() {
        return scores;
    }

    public void clear() {
        scores.clear();
    }
}
