package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    List<ProductModel> findAllByDeletedAtIsNull();

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductModel> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.stock = p.stock - :quantity"
            + " WHERE p.id = :id AND p.stock >= :quantity AND p.deletedAt IS NULL")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.stock = p.stock + :quantity"
            + " WHERE p.id = :id AND p.deletedAt IS NULL")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Query("SELECT p FROM ProductModel p"
            + " WHERE MOD(p.id, :divisor) = :remainder AND p.deletedAt IS NULL")
    List<ProductModel> findByIdModulo(
            @Param("divisor") int divisor, @Param("remainder") int remainder);

    @Query(value = "SELECT p.* FROM products p"
            + " LEFT JOIN product_metrics pm ON p.id = pm.product_id"
            + " WHERE p.deleted_at IS NULL"
            + " ORDER BY COALESCE(pm.like_count, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL",
            nativeQuery = true)
    Page<ProductModel> findAllSortedByMetricsLikeCountDesc(Pageable pageable);

    @Query(value = "SELECT p.* FROM products p"
            + " LEFT JOIN product_metrics pm ON p.id = pm.product_id"
            + " WHERE p.deleted_at IS NULL AND p.brand_id = :brandId"
            + " ORDER BY COALESCE(pm.like_count, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM products"
                    + " WHERE deleted_at IS NULL AND brand_id = :brandId",
            nativeQuery = true)
    Page<ProductModel> findAllByBrandIdSortedByMetricsLikeCountDesc(
            @Param("brandId") Long brandId, Pageable pageable);

    @Query(value = "SELECT COALESCE(pm.like_count, 0)"
            + " FROM products p"
            + " LEFT JOIN product_metrics pm ON p.id = pm.product_id"
            + " WHERE p.id = :productId",
            nativeQuery = true)
    long findLikeCountByProductId(@Param("productId") Long productId);

    @Query(value = "SELECT p.id AS productId, COALESCE(pm.like_count, 0) AS likeCount"
            + " FROM products p"
            + " LEFT JOIN product_metrics pm ON p.id = pm.product_id"
            + " WHERE p.id IN :productIds",
            nativeQuery = true)
    List<Object[]> findLikeCountsByProductIds(@Param("productIds") List<Long> productIds);
}
