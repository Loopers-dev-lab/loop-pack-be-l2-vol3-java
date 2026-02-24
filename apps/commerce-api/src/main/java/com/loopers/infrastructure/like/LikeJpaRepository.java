package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    Page<LikeModel> findByUserId(Long userId, Pageable pageable);

    long countByProductId(Long productId);
}
