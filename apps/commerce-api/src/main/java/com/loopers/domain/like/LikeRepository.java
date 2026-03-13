package com.loopers.domain.like;

import java.util.List;
import java.util.Map;

public interface LikeRepository {

    Like save(Like like);

    void deleteByMemberIdAndProductId(Long memberId, Long productId);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    Map<Long, Long> countByProductIds(List<Long> productIds);
}
