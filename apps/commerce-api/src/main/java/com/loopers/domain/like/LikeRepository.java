package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LikeRepository {
    Like save(Like like);

    boolean existsByMemberIdAndProductId(String memberId, Long productId);

    void deleteByMemberIdAndProductId(String memberId, Long productId);

    void deleteByProductIds(List<Long> productIds);

    Page<Like> findByMemberId(String memberId, Pageable pageable);
}
