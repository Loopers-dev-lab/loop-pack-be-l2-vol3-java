package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    // Command

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty " +
           "WHERE p.id = :id AND p.stockQuantity >= :qty AND p.deletedAt IS NULL")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE products p SET p.deleted_at = NOW() " +
           "WHERE p.brand_id = :brandId AND p.deleted_at IS NULL " +
           "ORDER BY p.id LIMIT :batchSize", nativeQuery = true)
    int softDeleteByBrandIdInBatch(@Param("brandId") Long brandId, @Param("batchSize") int batchSize);

    // Query
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") Long id);

    @Query("SELECT p FROM Product p " +
           "JOIN Brand b ON p.brandId = b.id " +
           "WHERE p.id = :id AND p.deletedAt IS NULL AND b.deletedAt IS NULL")
    Optional<Product> findActiveWithActiveBrand(@Param("id") Long id);

    List<Product> findAllByIdIn(Collection<Long> ids);

    @Query("SELECT p FROM Product p " +
            "JOIN Brand b ON p.brandId = b.id " +
            "WHERE p.id IN :ids AND p.deletedAt IS NULL AND b.deletedAt IS NULL")
    List<Product> findAllActiveWithActiveBrandByIdIn(@Param("ids") Collection<Long> ids);

    List<Product> findAllByBrandId(Long brandId);

    @Query(value = "SELECT p FROM Product p "
                 + "WHERE (:name IS NULL OR p.name LIKE %:name%) "
                 + "AND (:brandId IS NULL OR p.brandId = :brandId) "
                 + "AND (:deleted IS NULL OR (:deleted = true AND p.deletedAt IS NOT NULL) OR (:deleted = false AND p.deletedAt IS NULL)) "
                 + "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Product p "
                      + "WHERE (:name IS NULL OR p.name LIKE %:name%) "
                      + "AND (:brandId IS NULL OR p.brandId = :brandId) "
                      + "AND (:deleted IS NULL OR (:deleted = true AND p.deletedAt IS NOT NULL) OR (:deleted = false AND p.deletedAt IS NULL))")
    Page<Product> findAll(@Param("name") String name, @Param("brandId") Long brandId, @Param("deleted") Boolean deleted, Pageable pageable);

    @Query(value = "SELECT p FROM Product p "
                 + "WHERE p.deletedAt IS NULL "
                 + "AND (:brandId IS NULL OR p.brandId = :brandId)",
           countQuery = "SELECT COUNT(p) FROM Product p "
                      + "WHERE p.deletedAt IS NULL "
                      + "AND (:brandId IS NULL OR p.brandId = :brandId)")
    Page<Product> findAllActive(@Param("brandId") Long brandId, Pageable pageable);

    @Query(value = "SELECT p FROM Product p " +
                   "JOIN Brand b ON p.brandId = b.id " +
                   "WHERE p.deletedAt IS NULL AND b.deletedAt IS NULL " +
                   "AND (:brandId IS NULL OR p.brandId = :brandId)",
           countQuery = "SELECT COUNT(p) FROM Product p " +
                        "JOIN Brand b ON p.brandId = b.id " +
                        "WHERE p.deletedAt IS NULL AND b.deletedAt IS NULL " +
                        "AND (:brandId IS NULL OR p.brandId = :brandId)")
    Page<Product> findAllActiveWithActiveBrand(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT DISTINCT p.brandId FROM Product p " +
           "JOIN Brand b ON p.brandId = b.id " +
           "WHERE b.deletedAt IS NOT NULL AND p.deletedAt IS NULL")
    List<Long> findBrandIdsWithUncleanedProducts();
}
