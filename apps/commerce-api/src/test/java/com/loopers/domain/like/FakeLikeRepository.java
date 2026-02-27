package com.loopers.domain.like;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class FakeLikeRepository implements LikeRepository {

    private final List<Like> store = new ArrayList<>();

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
    public void delete(Like like) {
        store.remove(like);
    }

    @Override
    public long countByProductId(Long productId) {
        return store.stream()
            .filter(l -> l.getProductId().equals(productId))
            .count();
    }
}