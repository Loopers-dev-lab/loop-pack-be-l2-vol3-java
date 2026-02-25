package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    // Query
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
}
