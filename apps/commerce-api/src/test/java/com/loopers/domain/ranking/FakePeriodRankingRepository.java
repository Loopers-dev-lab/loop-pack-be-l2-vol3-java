package com.loopers.domain.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakePeriodRankingRepository implements PeriodRankingRepository {

    private final Map<String, Map<Long, Double>> store = new HashMap<>();
    private RuntimeException forcedFailure;

    public void addScore(String periodKey, Long productId, double score) {
        store.computeIfAbsent(periodKey, k -> new HashMap<>())
            .merge(productId, score, Double::sum);
    }

    public void failWith(RuntimeException exception) {
        this.forcedFailure = exception;
    }

    @Override
    public List<ProductRanking> findTopN(String periodKey, long offset, int limit) {
        if (forcedFailure != null) {
            throw forcedFailure;
        }
        Map<Long, Double> scores = store.getOrDefault(periodKey, Map.of());
        List<Map.Entry<Long, Double>> sorted = scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey))
            .toList();

        List<ProductRanking> result = new ArrayList<>();
        int startIndex = (int) offset;
        int endExclusive = Math.min(startIndex + limit, sorted.size());
        for (int i = startIndex; i < endExclusive; i++) {
            Map.Entry<Long, Double> entry = sorted.get(i);
            result.add(new ProductRanking(entry.getKey(), entry.getValue(), i + 1L));
        }
        return result;
    }

    @Override
    public long countByPeriod(String periodKey) {
        if (forcedFailure != null) {
            throw forcedFailure;
        }
        return store.getOrDefault(periodKey, Map.of()).size();
    }
}
