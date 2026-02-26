package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLike, Long> {
    Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    @Modifying
    @Query("DELETE FROM ProductLike pl WHERE pl.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
    Page<ProductLike> findAllByUserId(Long userId, Pageable pageable);
    List<ProductLike> findAllByProductId(Long productId);
    
    @Modifying
    @Query("DELETE FROM ProductLike pl WHERE pl.productId IN :productIds")
    void deleteAllByProductIdIn(@Param("productIds") List<Long> productIds);
}
