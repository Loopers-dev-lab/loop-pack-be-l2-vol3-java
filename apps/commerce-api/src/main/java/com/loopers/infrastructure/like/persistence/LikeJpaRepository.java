package com.loopers.infrastructure.like.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.like.Like;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    Slice<Like> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.productId = :productId")
    void deleteAllByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.productId IN :productIds")
    void deleteAllByProductIdIn(@Param("productIds") List<Long> productIds);

    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM Like l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countByProductIdIn(@Param("productIds") List<Long> productIds);
}
