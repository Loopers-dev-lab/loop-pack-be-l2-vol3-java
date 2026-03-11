package com.loopers.domain.like;

import java.util.Optional;

public interface LikeRepository {

    boolean existByUniqueId(Long productId, Long userId);

    Optional<Like> findByUniqueId(Long productId, Long userId);

    Like save(Like like);

    void delete(Like like);
}
