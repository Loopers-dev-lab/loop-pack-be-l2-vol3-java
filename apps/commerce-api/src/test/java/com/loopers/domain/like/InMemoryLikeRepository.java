package com.loopers.domain.like;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryLikeRepository implements LikeRepository {

    private final List<Like> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Like save(Like like) {
        store.add(like);
        return like;
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return store.stream()
                    .filter(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId))
                    .findFirst();
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        int before = store.size();
        store.removeIf(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
        return before - store.size();
    }

    @Override
    public void deleteAllByProductIdIn(List<Long> productIds) {
        store.removeIf(l -> productIds.contains(l.getProductId()));
    }

    @Override
    public List<Like> findByUserId(Long userId) {
        return store.stream()
                    .filter(l -> l.getUserId().equals(userId))
                    .toList();
    }
}
