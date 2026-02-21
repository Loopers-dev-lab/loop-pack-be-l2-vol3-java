package com.loopers.infrastructure.like.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.like.Like;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
