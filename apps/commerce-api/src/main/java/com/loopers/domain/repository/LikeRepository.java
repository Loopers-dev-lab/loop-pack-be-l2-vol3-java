package com.loopers.domain.repository;

import com.loopers.domain.model.like.Like;
import com.loopers.domain.model.user.UserId;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findByUserIdAndProductId(UserId userId, Long productId);

    void deleteByUserIdAndProductId(UserId userId, Long productId);

    boolean existsByUserIdAndProductId(UserId userId, Long productId);

    List<Like> findAllByUserId(UserId userId);
}
