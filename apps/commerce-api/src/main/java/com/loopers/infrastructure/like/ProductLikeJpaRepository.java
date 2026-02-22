package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLike, Long> {

    Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT pl FROM ProductLike pl " +
            "WHERE pl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM Product p WHERE p.id = pl.productId AND p.deletedAt IS NULL) " +
            "ORDER BY pl.createdAt DESC")
    List<ProductLike> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(pl) FROM ProductLike pl " +
            "WHERE pl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM Product p WHERE p.id = pl.productId AND p.deletedAt IS NULL)")
    long countActiveByUserId(@Param("userId") Long userId);
}
