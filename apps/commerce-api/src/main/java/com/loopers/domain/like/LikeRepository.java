package com.loopers.domain.like;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    Optional<Like> findByUserIdAndProductIdIncludingDeleted(Long userId, Long productId);

    Page<Like> findByUserId(Long userId, Pageable pageable);
}
