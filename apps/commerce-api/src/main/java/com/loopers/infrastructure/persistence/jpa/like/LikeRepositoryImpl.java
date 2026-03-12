package com.loopers.infrastructure.persistence.jpa.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LikeRepository 구현체.
 * JPA를 사용하여 Like 도메인 객체를 영속화.
 * Domain ↔ JPA Entity 변환은 LikeMapper를 통해 수행.
 */
@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository jpaRepository;

    @Override
    public Like save(Like like) {
        LikeJpaEntity entity = LikeMapper.toJpaEntity(like);
        LikeJpaEntity saved = jpaRepository.save(entity);
        return LikeMapper.toDomain(saved);
    }

    @Override
    public void delete(Like like) {
        jpaRepository.deleteById(like.getId());
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return jpaRepository.findByUserIdAndProductId(userId, productId)
            .map(LikeMapper::toDomain);
    }

    @Override
    public boolean exists(Long userId, Long productId) {
        return jpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return jpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> results = jpaRepository.countByProductIdIn(productIds);
        Map<Long, Long> countMap = new HashMap<>();

        for (Object[] row : results) {
            Long productId = (Long) row[0];
            Long count = (Long) row[1];
            countMap.put(productId, count);
        }

        return countMap;
    }
}
