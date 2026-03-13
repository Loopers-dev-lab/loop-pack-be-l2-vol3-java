package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public void deleteByMemberIdAndProductId(Long memberId, Long productId) {
        likeJpaRepository.deleteByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return likeJpaRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> results = likeJpaRepository.countByProductIdsGroupByProductId(productIds);
        Map<Long, Long> map = results.stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Number) row[1]).longValue()
            ));
        for (Long productId : productIds) {
            map.putIfAbsent(productId, 0L);
        }
        return map;
    }
}
