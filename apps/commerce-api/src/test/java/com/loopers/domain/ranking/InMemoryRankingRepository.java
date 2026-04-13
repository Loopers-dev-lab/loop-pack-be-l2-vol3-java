package com.loopers.domain.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryRankingRepository implements RankingRepository {
    private final Map<String, Map<Long, Double>> store = new HashMap<>();

    public void addScore(String key, Long productId, double score) {
        store.computeIfAbsent(key, k -> new HashMap<>())
             .merge(productId, score, Double::sum);
    }

    @Override
    public List<RankingEntry> getTopRankings(String key, int offset, int size) {
        Map<Long, Double> scores = store.get(key);
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<Long, Double>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .toList();

        List<RankingEntry> result = new ArrayList<>();
        int end = Math.min(offset + size, sorted.size());
        for (int i = offset; i < end; i++) {
            Map.Entry<Long, Double> entry = sorted.get(i);
            result.add(new RankingEntry(entry.getKey(), entry.getValue(), (long) i));
        }
        return result;
    }

    @Override
    public Long getRank(String key, Long productId) {
        Map<Long, Double> scores = store.get(key);
        if (scores == null || !scores.containsKey(productId)) {
            return null;
        }

        long rank = scores.entrySet().stream()
                .filter(e -> e.getValue() > scores.get(productId))
                .count();
        return rank;
    }

    @Override
    public Double getScore(String key, Long productId) {
        Map<Long, Double> scores = store.get(key);
        if (scores == null) return null;
        return scores.get(productId);
    }
}
