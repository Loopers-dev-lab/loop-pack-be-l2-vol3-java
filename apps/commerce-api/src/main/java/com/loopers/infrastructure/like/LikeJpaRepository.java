package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT l FROM LikeModel l JOIN FETCH l.product p JOIN FETCH p.brand WHERE l.userId = :userId AND p.deletedAt IS NULL")
    List<LikeModel> findByUserIdWithProduct(@Param("userId") Long userId);

    long countByProductId(Long productId);

    @Query("SELECT l.product.id, COUNT(l) FROM LikeModel l WHERE l.product.id IN :productIds GROUP BY l.product.id")
    List<Object[]> countByProductIdIn(@Param("productIds") List<Long> productIds);
}
