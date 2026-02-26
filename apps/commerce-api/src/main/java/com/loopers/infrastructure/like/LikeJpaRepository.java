package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeJpaEntity, Long> {

    Optional<LikeJpaEntity> findByUserIdAndProductId(String userId, Long productId);

    boolean existsByUserIdAndProductId(String userId, Long productId);

    void deleteByUserIdAndProductId(String userId, Long productId);

    List<LikeJpaEntity> findAllByUserId(String userId);

    @Query("SELECT l, p, b FROM LikeJpaEntity l " +
            "JOIN ProductJpaEntity p ON l.productId = p.id " +
            "JOIN BrandJpaEntity b ON p.brandId = b.id " +
            "WHERE l.userId = :userId AND p.deletedAt IS NULL")
    List<Object[]> findAllWithProductByUserId(@Param("userId") String userId);
}
