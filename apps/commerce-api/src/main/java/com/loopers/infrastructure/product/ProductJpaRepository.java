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
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCountIfPositive(@Param("id") Long id);

    @Query(value = "SELECT id FROM products WHERE brand_id = :brandId AND deleted_at IS NULL ORDER BY id LIMIT :batchSize",
           nativeQuery = true)
    List<Long> findIdsByBrandIdForCleanup(@Param("brandId") Long brandId, @Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id IN :ids")
    int softDeleteByIds(@Param("ids") List<Long> ids);

    @Modifying
    @Query(value = "UPDATE products p " +
            "SET p.like_count = (" +
            "  SELECT COUNT(*) FROM likes l WHERE l.product_id = p.id" +
            ") WHERE p.deleted_at IS NULL " +
            "AND p.like_count != (" +
            "  SELECT COUNT(*) FROM likes l2 WHERE l2.product_id = p.id" +
            ")",
            nativeQuery = true)
    int reconcileLikeCountFromLikes();

    // Query
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") Long id);

    @Query("SELECT p FROM Product p " +
           "JOIN Brand b ON p.brandId = b.id " +
           "WHERE p.id = :id AND p.deletedAt IS NULL AND b.deletedAt IS NULL")
    Optional<Product> findActiveWithActiveBrand(@Param("id") Long id);

    @Query("SELECT COUNT(p) > 0 FROM Product p " +
           "JOIN Brand b ON p.brandId = b.id " +
           "WHERE p.id = :id AND p.deletedAt IS NULL AND b.deletedAt IS NULL")
    boolean existsActiveWithActiveBrand(@Param("id") Long id);

    List<Product> findAllByIdIn(Collection<Long> ids);

    @Query("SELECT p FROM Product p " +
            "JOIN Brand b ON p.brandId = b.id " +
            "WHERE p.id IN :ids AND p.deletedAt IS NULL AND b.deletedAt IS NULL")
    List<Product> findAllActiveWithActiveBrandByIdIn(@Param("ids") Collection<Long> ids);

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

    @Query(value = "SELECT p.* FROM products p " +
                   "JOIN brands b ON p.brand_id = b.id " +
                   "WHERE p.deleted_at IS NULL AND b.deleted_at IS NULL " +
                   "AND (:brandId IS NULL OR p.brand_id = :brandId) " +
                   "AND (:cursor IS NULL OR p.id < :cursor) " +
                   "ORDER BY p.id DESC " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<Product> findAllActiveCursor(@Param("brandId") Long brandId,
                                     @Param("cursor") Long cursor,
                                     @Param("limit") int limit);
}
