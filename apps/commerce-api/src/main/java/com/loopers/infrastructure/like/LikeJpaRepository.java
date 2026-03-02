package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    Page<LikeModel> findByUserId(Long userId, Pageable pageable);

    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM LikeModel l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countGroupByProductId(@Param("productIds") List<Long> productIds);
}
