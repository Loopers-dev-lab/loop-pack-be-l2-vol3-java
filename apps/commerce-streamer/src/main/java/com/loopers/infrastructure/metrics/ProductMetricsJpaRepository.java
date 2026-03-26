package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 상품 지표 JPA Repository.
 *
 * <p>Native upsert 쿼리를 사용하여 INSERT ON DUPLICATE KEY UPDATE로
 * 레코드가 없으면 생성, 있으면 증분 갱신한다.</p>
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, 1, 0, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void incrementViewCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, 0, 1, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, 0, 0, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()",
            nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, view_count, like_count, order_count, order_amount, updated_at) " +
            "VALUES (:productId, 0, 0, 1, :amount, NOW()) " +
            "ON DUPLICATE KEY UPDATE order_count = order_count + 1, order_amount = order_amount + :amount, updated_at = NOW()",
            nativeQuery = true)
    void incrementOrderCount(@Param("productId") Long productId, @Param("amount") long amount);

    @Query(value = "SELECT pm.product_id, pm.like_count AS metricsCount, p.like_count AS productsCount " +
            "FROM product_metrics pm " +
            "JOIN products p ON pm.product_id = p.product_id " +
            "WHERE pm.like_count != p.like_count",
            nativeQuery = true)
    List<Object[]> findLikeCountMismatchesRaw();

    @Modifying
    @Query(value = "UPDATE product_metrics SET like_count = :likeCount, updated_at = NOW() WHERE product_id = :productId",
            nativeQuery = true)
    void forceUpdateLikeCount(@Param("productId") Long productId, @Param("likeCount") long likeCount);
}
