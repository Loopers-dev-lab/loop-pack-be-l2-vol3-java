package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface LikeRepository {
    Like save(Like like);

    boolean existsByMemberIdAndProductId(String memberId, UUID productId);

    void deleteByMemberIdAndProductId(String memberId, UUID productId);

    void deleteByProductIds(List<UUID> productIds);

    Page<Like> findByMemberId(String memberId, Pageable pageable);
}
