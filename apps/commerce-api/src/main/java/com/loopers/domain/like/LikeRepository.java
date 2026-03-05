package com.loopers.domain.like;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LikeRepository {
    Like save(Like like);
    void delete(Like like);
    Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId);
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);
    List<Like> findAllByMemberId(Long memberId);
    void deleteAllByProductId(Long productId);
    void deleteAllByProductIdIn(Collection<Long> productIds);
    long countByProductId(Long productId);
    Map<Long, Long> countByProductIds(Collection<Long> productIds);
}
