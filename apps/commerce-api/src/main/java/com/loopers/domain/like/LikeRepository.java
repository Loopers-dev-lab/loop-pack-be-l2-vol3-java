package com.loopers.domain.like;

import java.util.Optional;

public interface LikeRepository {

    // Command
    Like save(Like like);
    void delete(Like like);

    // Query
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
}
