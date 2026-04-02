package com.loopers.domain.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FakeWaitingQueueRepository implements WaitingQueueRepository {

    private final Map<Long, Double> userScores = new HashMap<>();

    @Override
    public boolean add(Long userId, double score) {
        if (userScores.containsKey(userId)) {
            return false;
        }
        userScores.put(userId, score);
        return true;
    }

    @Override
    public Long rank(Long userId) {
        Double score = userScores.get(userId);
        if (score == null) {
            return null;
        }
        long rank = 0;
        for (Map.Entry<Long, Double> entry : userScores.entrySet()) {
            if (entry.getValue() < score || (entry.getValue().equals(score) && entry.getKey() < userId)) {
                rank++;
            }
        }
        return rank;
    }

    @Override
    public boolean addIfNotFull(Long userId, double score, long maxQueueSize) {
        if (userScores.containsKey(userId)) {
            return true;
        }
        if (userScores.size() >= maxQueueSize) {
            return false;
        }
        userScores.put(userId, score);
        return true;
    }

    @Override
    public long size() {
        return userScores.size();
    }

    @Override
    public List<Long> popMin(int count) {
        List<Map.Entry<Long, Double>> sorted = userScores.entrySet().stream()
            .sorted(Comparator.comparingDouble(Map.Entry<Long, Double>::getValue)
                .thenComparingLong(Map.Entry::getKey))
            .toList();

        List<Long> result = new ArrayList<>();
        for (int i = 0; i < count && i < sorted.size(); i++) {
            result.add(sorted.get(i).getKey());
        }
        for (Long userId : result) {
            userScores.remove(userId);
        }
        return result;
    }
}
