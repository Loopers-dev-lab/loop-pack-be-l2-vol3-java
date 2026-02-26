package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    public LikeRepositoryImpl(LikeJpaRepository likeJpaRepository) {
        this.likeJpaRepository = likeJpaRepository;
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public void delete(LikeModel like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public Page<LikeModel> findByUserId(Long userId, Pageable pageable) {
        return likeJpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = productIds instanceof List<?> ? (List<Long>) productIds : List.copyOf(productIds);
        List<Object[]> rows = likeJpaRepository.countGroupByProductId(ids);
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }
}
