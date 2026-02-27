package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.memberId = :memberId AND l.productId = :productId")
    void deleteByMemberIdAndProductId(@Param("memberId") Long memberId, @Param("productId") Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM Like l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countByProductIdsGroupByProductId(@Param("productIds") List<Long> productIds);
}
