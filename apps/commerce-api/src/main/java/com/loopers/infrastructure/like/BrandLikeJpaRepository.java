package com.loopers.infrastructure.like;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * BrandLikeJpaRepository
 * Spring Data JPA Repository (BrandLikeEntity 기준)
 */
public interface BrandLikeJpaRepository extends JpaRepository<BrandLikeEntity, Long> {

    Optional<BrandLikeEntity> findByUserIdAndBrandId(Long userId, Long brandId);

    boolean existsByUserIdAndBrandId(Long userId, Long brandId);

    @Query("SELECT bl FROM BrandLikeEntity bl " +
            "WHERE bl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM BrandEntity b WHERE b.id = bl.brandId AND b.deletedAt IS NULL AND b.status = 'ACTIVE') " +
            "ORDER BY bl.createdAt DESC")
    List<BrandLikeEntity> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(bl) FROM BrandLikeEntity bl " +
            "WHERE bl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM BrandEntity b WHERE b.id = bl.brandId AND b.deletedAt IS NULL AND b.status = 'ACTIVE')")
    long countActiveByUserId(@Param("userId") Long userId);
}

