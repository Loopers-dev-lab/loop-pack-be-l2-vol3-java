package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    // Command
    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId AND l.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    // Query
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    @Query(value = "SELECT l FROM Like l WHERE l.userId = :userId "
                 + "AND l.productId IN (SELECT p.id FROM Product p WHERE p.deletedAt IS NULL) "
                 + "ORDER BY l.createdAt DESC",
           countQuery = "SELECT COUNT(l) FROM Like l WHERE l.userId = :userId "
                      + "AND l.productId IN (SELECT p.id FROM Product p WHERE p.deletedAt IS NULL)")
    Page<Like> findAllByUserIdWithActiveProduct(@Param("userId") Long userId, Pageable pageable);
}
