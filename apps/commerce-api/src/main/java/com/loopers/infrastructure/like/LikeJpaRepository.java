package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    Optional<LikeModel> findByUserIdAndProductIdAndDeletedAtIsNull(Long userId, Long productId);

    @Query("SELECT l FROM LikeModel l WHERE l.userId = :userId AND l.deletedAt IS NULL " +
           "AND EXISTS (SELECT 1 FROM ProductModel p WHERE p.id = l.productId AND p.deletedAt IS NULL)")
    Page<LikeModel> findActiveLikesWithActiveProduct(@Param("userId") Long userId, Pageable pageable);
}
