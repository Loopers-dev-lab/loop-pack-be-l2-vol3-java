package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    List<Like> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.productId = :productId")
    void deleteAllByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.productId IN :productIds")
    void deleteAllByProductIdIn(@Param("productIds") Collection<Long> productIds);
}
