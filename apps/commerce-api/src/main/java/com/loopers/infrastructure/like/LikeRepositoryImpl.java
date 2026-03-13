package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId) {
        return likeJpaRepository.findByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return likeJpaRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public List<Like> findAllByMemberId(Long memberId) {
        return likeJpaRepository.findAllByMemberId(memberId);
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        likeJpaRepository.deleteAllByProductId(productId);
    }

    @Override
    public void deleteAllByProductIdIn(Collection<Long> productIds) {
        if (productIds.isEmpty()) return;
        likeJpaRepository.deleteAllByProductIdIn(productIds);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        return likeJpaRepository.countByProductIdIn(productIds).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
            ));
    }
}
