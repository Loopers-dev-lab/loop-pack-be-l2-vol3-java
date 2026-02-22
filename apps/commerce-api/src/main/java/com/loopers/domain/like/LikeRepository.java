package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    Slice<Like> findAllByUserId(Long userId, Pageable pageable);

    List<Long> findProductIdsByUserIdAndProductIdIn(Long userId, List<Long> productIds);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void delete(Like like);

    void deleteAllByProductId(Long productId);

    void deleteAllByProductIdIn(List<Long> productIds);
}
