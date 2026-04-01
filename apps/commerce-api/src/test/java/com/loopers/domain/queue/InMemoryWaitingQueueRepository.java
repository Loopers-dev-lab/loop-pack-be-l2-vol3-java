package com.loopers.domain.queue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWaitingQueueRepository implements WaitingQueueRepository {

    private final Map<Long, Double> scores = new ConcurrentHashMap<>();
    private final TreeMap<Double, LinkedHashSet<Long>> sortedEntries = new TreeMap<>();

    @Override
    public boolean enqueue(Long userId, double score) {
        if (scores.containsKey(userId)) {
            return false;
        }
        scores.put(userId, score);
        sortedEntries.computeIfAbsent(score, k -> new LinkedHashSet<>()).add(userId);
        return true;
    }

    @Override
    public Long getRank(Long userId) {
        if (!scores.containsKey(userId)) {
            return null;
        }
        double userScore = scores.get(userId);
        long rank = 0;
        for (Map.Entry<Double, LinkedHashSet<Long>> entry : sortedEntries.entrySet()) {
            if (entry.getKey() < userScore) {
                rank += entry.getValue().size();
            } else if (entry.getKey() == userScore) {
                for (Long id : entry.getValue()) {
                    if (id.equals(userId)) {
                        return rank;
                    }
                    rank++;
                }
            }
        }
        return rank;
    }

    @Override
    public long getTotalCount() {
        return scores.size();
    }

    @Override
    public Set<Long> dequeue(int count) {
        Set<Long> result = new LinkedHashSet<>();
        while (result.size() < count && !sortedEntries.isEmpty()) {
            Map.Entry<Double, LinkedHashSet<Long>> first = sortedEntries.firstEntry();
            var iterator = first.getValue().iterator();
            while (iterator.hasNext() && result.size() < count) {
                Long userId = iterator.next();
                iterator.remove();
                scores.remove(userId);
                result.add(userId);
            }
            if (first.getValue().isEmpty()) {
                sortedEntries.pollFirstEntry();
            }
        }
        return result;
    }
}
