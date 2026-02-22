package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeJpaEntity, Long> {
    Optional<LikeJpaEntity> findByUserIdAndProductId(Long userId, Long productId);
    long countByProductId(Long productId);
}
