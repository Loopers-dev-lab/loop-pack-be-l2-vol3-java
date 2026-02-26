package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LikeRepository {
    Like save(Like like);

    boolean existsByMemberIdAndProductId(String memberId, Long productId);

    void deleteByMemberIdAndProductId(String memberId, Long productId);

    Page<Like> findByMemberId(String memberId, Pageable pageable);
}
