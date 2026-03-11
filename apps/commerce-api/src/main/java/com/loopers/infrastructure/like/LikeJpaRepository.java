package com.loopers.infrastructure.like;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, Long> {

    boolean existsByRefProductIdAndRefUserId(Long productId, Long userId);

    Optional<LikeEntity> findByRefProductIdAndRefUserId(Long productId, Long userId);
}
