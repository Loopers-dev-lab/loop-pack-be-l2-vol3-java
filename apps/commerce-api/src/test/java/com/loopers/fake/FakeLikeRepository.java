package com.loopers.fake;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 테스트용 Fake LikeRepository.
 * Map 기반 in-memory 구현.
 */
public class FakeLikeRepository implements LikeRepository {

    private final Map<Long, Like> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Like save(Like like) {
        Long id = idGenerator.getAndIncrement();
        Like saved = Like.reconstitute(
            id,
            like.getUserId(),
            like.getProductId(),
            like.getCreatedAt()
        );
        store.put(id, saved);
        return saved;
    }

    @Override
    public void delete(Like like) {
        store.remove(like.getId());
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
            .filter(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId))
            .findFirst();
    }

    @Override
    public boolean exists(Long userId, Long productId) {
        return store.values().stream()
            .anyMatch(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
    }

    @Override
    public long countByProductId(Long productId) {
        return store.values().stream()
            .filter(l -> l.getProductId().equals(productId))
            .count();
    }

    @Override
    public Map<Long, Long> countByProductIds(List<Long> productIds) {
        return store.values().stream()
            .filter(l -> productIds.contains(l.getProductId()))
            .collect(Collectors.groupingBy(
                Like::getProductId,
                Collectors.counting()
            ));
    }

    /**
     * 테스트용: 저장소 초기화
     */
    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    /**
     * 테스트용: 저장된 좋아요 수 조회
     */
    public int size() {
        return store.size();
    }
}
