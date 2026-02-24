package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {
    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        LikeJpaEntity entity = LikeJpaEntity.from(like);
        LikeJpaEntity saved = likeJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.findByUserIdAndProductId(like.getUserId(), like.getProductId())
                .ifPresent(likeJpaRepository::delete);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(LikeJpaEntity::toDomain);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public List<Like> findByUserId(Long userId) {
        return likeJpaRepository.findByUserId(userId).stream()
                .map(LikeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Map<Long, Long> countByProductIdIn(List<Long> productIds) {
        List<Object[]> results = likeJpaRepository.countByProductIdIn(productIds);
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] result : results) {
            Long productId = (Long) result[0];
            Long count = (Long) result[1];
            countMap.put(productId, count);
        }
        return countMap;
    }

    @Override
    public Set<Long> findLikedProductIds(Long userId, List<Long> productIds) {
        return new HashSet<>(likeJpaRepository.findLikedProductIds(userId, productIds));
    }
}
