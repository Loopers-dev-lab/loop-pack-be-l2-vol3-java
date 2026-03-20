package com.loopers.fake;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 테스트용 Fake LikeCountCacheService.
 * Map 기반 in-memory 구현.
 */
public class FakeLikeCountCacheService {

    private final Map<Long, Long> store = new HashMap<>();
    private final Set<Long> dirtySet = new HashSet<>();

    public Long increment(Long productId) {
        Long current = store.getOrDefault(productId, 0L);
        Long newValue = current + 1;
        store.put(productId, newValue);
        dirtySet.add(productId);
        return newValue;
    }

    public Long decrement(Long productId) {
        Long current = store.getOrDefault(productId, 0L);
        Long newValue = Math.max(0, current - 1);
        store.put(productId, newValue);
        dirtySet.add(productId);
        return newValue;
    }

    public Optional<Long> get(Long productId) {
        return Optional.ofNullable(store.get(productId));
    }

    public Map<Long, Long> getMultiple(List<Long> productIds) {
        Map<Long, Long> result = new HashMap<>();
        for (Long productId : productIds) {
            Long value = store.get(productId);
            if (value != null) {
                result.put(productId, value);
            }
        }
        return result;
    }

    public void set(Long productId, Long count) {
        store.put(productId, count);
    }

    public void delete(Long productId) {
        store.remove(productId);
    }

    public void markDirty(Long productId) {
        dirtySet.add(productId);
    }

    public Set<Long> getDirtyProductIds() {
        return new HashSet<>(dirtySet);
    }

    public void clearDirtyProductIds() {
        dirtySet.clear();
    }

    public void removeDirty(Long productId) {
        dirtySet.remove(productId);
    }

    public void clear() {
        store.clear();
        dirtySet.clear();
    }
}
