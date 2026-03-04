package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId AND l.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
    void deleteAllByProductIdIn(List<Long> productIds);
    List<Like> findByUserId(Long userId);
}
