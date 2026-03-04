package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {

    Like save(Like like);

    void delete(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    List<Like> findAllByUserId(Long userId);
}
