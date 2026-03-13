package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId);
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);
    List<Like> findAllByMemberId(Long memberId);
    void deleteAllByProductId(Long productId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.productId IN :productIds")
    void deleteAllByProductIdIn(@Param("productIds") Collection<Long> productIds);

    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM Like l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countByProductIdIn(@Param("productIds") Collection<Long> productIds);
}
