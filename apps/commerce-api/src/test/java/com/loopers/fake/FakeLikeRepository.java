package com.loopers.fake;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeLikeRepository implements LikeRepository {

    private final Map<Long, Like> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public Like save(Like like) {
        if (like.getId() == null) {
            long id = sequence++;
            ReflectionTestUtils.setField(like, "id", id);
        }
        store.put(like.getId(), like);
        return like;
    }

    @Override
    public void delete(Like like) {
        store.remove(like.getId());
    }

    @Override
    public Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId) {
        return store.values().stream()
                .filter(like -> like.getMemberId().equals(memberId) && like.getProductId().equals(productId))
                .findFirst();
    }

    @Override
    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return store.values().stream()
                .anyMatch(like -> like.getMemberId().equals(memberId) && like.getProductId().equals(productId));
    }

    @Override
    public List<Like> findAllByMemberId(Long memberId) {
        return store.values().stream()
                .filter(like -> like.getMemberId().equals(memberId))
                .toList();
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        List<Long> keysToRemove = store.entrySet().stream()
                .filter(entry -> entry.getValue().getProductId().equals(productId))
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(store::remove);
    }
}
