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
    int decrementLikeCount(@Param("id") Long id);

    // Query
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") Long id);

    List<Product> findAllByIdIn(Collection<Long> ids);

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
}
