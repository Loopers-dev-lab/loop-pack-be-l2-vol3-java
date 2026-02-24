package com.loopers.infrastructure.like;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ProductLikeJpaRepository
 * Spring Data JPA Repository (ProductLikeEntity 기준)
 */
public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeEntity, Long> {

    Optional<ProductLikeEntity> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT pl FROM ProductLikeEntity pl " +
            "WHERE pl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM ProductEntity p WHERE p.id = pl.productId AND p.deletedAt IS NULL) " +
            "ORDER BY pl.createdAt DESC")
    List<ProductLikeEntity> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(pl) FROM ProductLikeEntity pl " +
            "WHERE pl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM ProductEntity p WHERE p.id = pl.productId AND p.deletedAt IS NULL)")
    long countActiveByUserId(@Param("userId") Long userId);
}

