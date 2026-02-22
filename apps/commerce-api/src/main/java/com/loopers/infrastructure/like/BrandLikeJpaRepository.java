package com.loopers.infrastructure.like;

import com.loopers.domain.like.BrandLike;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandLikeJpaRepository extends JpaRepository<BrandLike, Long> {

    Optional<BrandLike> findByUserIdAndBrandId(Long userId, Long brandId);

    boolean existsByUserIdAndBrandId(Long userId, Long brandId);

    @Query("SELECT bl FROM BrandLike bl " +
            "WHERE bl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM Brand b WHERE b.id = bl.brandId AND b.deletedAt IS NULL AND b.status = 'ACTIVE') " +
            "ORDER BY bl.createdAt DESC")
    List<BrandLike> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(bl) FROM BrandLike bl " +
            "WHERE bl.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM Brand b WHERE b.id = bl.brandId AND b.deletedAt IS NULL AND b.status = 'ACTIVE')")
    long countActiveByUserId(@Param("userId") Long userId);
}
