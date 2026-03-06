package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LikeRepository {

    // Command
    Like save(Like like);
    int deleteByUserIdAndProductId(Long userId, Long productId);

    // Query
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    Page<Like> findAllByUserIdWithActiveProduct(Long userId, Pageable pageable);
}
