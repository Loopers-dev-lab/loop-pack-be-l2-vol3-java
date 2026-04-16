package com.loopers.fake;

import com.loopers.application.ranking.RankingBatchProcessor;
import com.loopers.application.ranking.RankingKeyGenerator;
import com.loopers.application.ranking.RankingProperties;
import com.loopers.application.ranking.RankingScoreCalculator;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * 테스트용 FakeRankingBatchProcessor.
 * Redis 없이 메모리에 점수를 저장하고, 배치 호출 횟수를 추적합니다.
 */
public class FakeRankingBatchProcessor extends RankingBatchProcessor {

    private final Map<String, Map<Long, Double>> scores = new HashMap<>();
    private final RankingKeyGenerator keyGenerator;
    private final RankingScoreCalculator calculator;
    private final List<List<ScoreEntry>> batchCallHistory = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public FakeRankingBatchProcessor() {
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
    public void addScoresBatch(List<ScoreEntry> scoreEntries) {
        addScoresBatch(scoreEntries, LocalDate.now());
    }

    @Override
    public void addScoresBatch(List<ScoreEntry> scoreEntries, LocalDate date) {
        if (scoreEntries == null || scoreEntries.isEmpty()) {
            return;
        }

        // 배치 호출 기록
        batchCallHistory.add(new ArrayList<>(scoreEntries));

        // 점수 합산
        String key = keyGenerator.generateDailyKey(date);
        for (ScoreEntry entry : scoreEntries) {
            double score = calculator.calculate(entry.eventType(), entry.eventValue());
            if (score != 0.0) {
                scores.computeIfAbsent(key, k -> new HashMap<>())
                      .merge(entry.productId(), score, Double::sum);
            }
        }
    }

    public Double getScore(String key, Long productId) {
        Map<Long, Double> keyScores = scores.get(key);
        return keyScores != null ? keyScores.get(productId) : null;
    }

    public int getBatchCallCount() {
        return batchCallHistory.size();
    }

    public List<List<ScoreEntry>> getBatchCallHistory() {
        return batchCallHistory;
    }

    public void clear() {
        scores.clear();
        batchCallHistory.clear();
    }
}