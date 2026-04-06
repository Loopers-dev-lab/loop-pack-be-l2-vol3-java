package com.loopers.domain.ranking;

import java.util.HashMap;
import java.util.Map;

public class FakeRankingRepository implements RankingRepository {

    private final Map<String, Map<Long, Double>> store = new HashMap<>();
    private final Map<String, Long> ttlStore = new HashMap<>();

    @Override
    public void putScore(String key, Long productId, double compositeScore, long ttlSeconds) {
        store.computeIfAbsent(key, k -> new HashMap<>())
            .put(productId, compositeScore);
        ttlStore.putIfAbsent(key, ttlSeconds);
    }

    public double getScore(String key, Long productId) {
        return store.getOrDefault(key, Map.of()).getOrDefault(productId, 0.0);
    }

    public Map<Long, Double> getAll(String key) {
        return store.getOrDefault(key, Map.of());
    }

    public Long getTtl(String key) {
        return ttlStore.get(key);
    }
}
