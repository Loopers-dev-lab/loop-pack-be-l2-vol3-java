package com.loopers.domain.waitingroom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeWaitingQueue implements WaitingQueue {

    private final Map<Long, Long> store = new LinkedHashMap<>();
    private long timestampSequence = 1;

    @Override
    public boolean enter(Long userId) {
        if (store.containsKey(userId)) {
            return false;
        }
        store.put(userId, timestampSequence++);
        return true;
    }

    @Override
    public Long getRank(Long userId) {
        if (!store.containsKey(userId)) {
            return null;
        }
        long rank = 0;
        for (Long key : store.keySet()) {
            if (key.equals(userId)) {
                return rank;
            }
            rank++;
        }
        return null;
    }

    @Override
    public long getTotalWaiting() {
        return store.size();
    }

    @Override
    public boolean cancel(Long userId) {
        return store.remove(userId) != null;
    }

    @Override
    public List<Long> popFront(int count) {
        List<Long> result = new ArrayList<>();
        var iterator = store.entrySet().iterator();
        while (iterator.hasNext() && result.size() < count) {
            result.add(iterator.next().getKey());
            iterator.remove();
        }
        return result;
    }

    @Override
    public List<WaitingEntry> popFrontWithScores(int count) {
        List<WaitingEntry> result = new ArrayList<>();
        var iterator = store.entrySet().iterator();
        while (iterator.hasNext() && result.size() < count) {
            var entry = iterator.next();
            result.add(new WaitingEntry(entry.getKey(), entry.getValue()));
            iterator.remove();
        }
        return result;
    }
}
