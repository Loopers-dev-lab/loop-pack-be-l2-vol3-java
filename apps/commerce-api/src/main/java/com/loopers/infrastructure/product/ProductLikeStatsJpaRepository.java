package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductLikeStatsJpaRepository extends JpaRepository<ProductLikeStats, Long> {

    @Modifying
    @Query(value = "REPLACE INTO product_like_stats (product_id, like_count, synced_at) "
        + "SELECT l.product_id, COUNT(*), NOW() FROM likes l GROUP BY l.product_id", nativeQuery = true)
    void syncAllFromLikes();

    @Modifying
    @Query(value = "UPDATE product p JOIN product_like_stats pls ON p.id = pls.product_id "
        + "SET p.like_count = pls.like_count WHERE p.like_count != pls.like_count AND p.deleted_at IS NULL", nativeQuery = true)
    int correctProductLikeCounts();
}
