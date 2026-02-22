package com.loopers.infrastructure.product.persistence;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.product.Product;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long productId);

    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds);

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    Slice<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(Long productId);

    @Query("SELECT p FROM Product p LEFT JOIN Like l ON l.productId = p.id "
         + "WHERE p.deletedAt IS NULL "
         + "GROUP BY p ORDER BY COUNT(l) DESC, p.createdAt DESC")
    Slice<Product> findAllActiveOrderByLikeCountDesc(Pageable pageable);

    @Query("SELECT p FROM Product p LEFT JOIN Like l ON l.productId = p.id "
         + "WHERE p.deletedAt IS NULL AND p.brandId = :brandId "
         + "GROUP BY p ORDER BY COUNT(l) DESC, p.createdAt DESC")
    Slice<Product> findAllActiveByBrandIdOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = :now WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId, @Param("now") ZonedDateTime now);
}
