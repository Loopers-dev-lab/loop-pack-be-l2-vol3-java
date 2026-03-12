package com.loopers.infrastructure.persistence.jpa.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Like JPA Repository.
 * Spring Data JPA를 사용한 영속성 계층.
 */
public interface LikeJpaRepository extends JpaRepository<LikeJpaEntity, Long> {

    Optional<LikeJpaEntity> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM LikeJpaEntity l " +
           "WHERE l.productId IN :productIds " +
           "GROUP BY l.productId")
    List<Object[]> countByProductIdIn(@Param("productIds") List<Long> productIds);
}
