package com.loopers.domain.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeRankingRepository implements RankingRepository {

    private final Map<String, Map<Long, Double>> store = new HashMap<>();

    public void addScore(String key, Long productId, double score) {
        store.computeIfAbsent(key, k -> new HashMap<>())
            .merge(productId, score, Double::sum);
    }

    @Override
    public List<ProductRanking> getTopN(String key, long start, long stop) {
        Map<Long, Double> scores = store.getOrDefault(key, Map.of());
        List<Map.Entry<Long, Double>> sorted = scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
            .toList();

        List<ProductRanking> result = new ArrayList<>();
        for (int i = (int) start; i <= Math.min(stop, sorted.size() - 1); i++) {
            Map.Entry<Long, Double> entry = sorted.get(i);
            result.add(new ProductRanking(entry.getKey(), entry.getValue(), i + 1));
        }
        return result;
    }

    @Override
    public Optional<Long> getRank(String key, Long productId) {
        Map<Long, Double> scores = store.getOrDefault(key, Map.of());
        if (!scores.containsKey(productId)) {
            return Optional.empty();
        }

        List<Map.Entry<Long, Double>> sorted = scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
            .toList();

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(productId)) {
                return Optional.of((long) i + 1);
            }
        }
        return Optional.empty();
    }

    @Override
    public long getTotalCount(String key) {
        return store.getOrDefault(key, Map.of()).size();
    }
}
