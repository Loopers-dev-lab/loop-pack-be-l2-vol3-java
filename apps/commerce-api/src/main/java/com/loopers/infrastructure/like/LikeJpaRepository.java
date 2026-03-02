package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    long countByProductId(Long productId);
    List<Like> findByUserId(Long userId);

    @Query("SELECT l.productId, COUNT(l) FROM Like l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countByProductIdIn(@Param("productIds") List<Long> productIds);

    @Query("SELECT l.productId FROM Like l WHERE l.userId = :userId AND l.productId IN :productIds")
    List<Long> findLikedProductIds(@Param("userId") Long userId, @Param("productIds") List<Long> productIds);
}
