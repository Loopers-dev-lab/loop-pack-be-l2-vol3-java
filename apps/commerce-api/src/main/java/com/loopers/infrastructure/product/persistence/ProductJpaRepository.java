package com.loopers.infrastructure.product.persistence;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.product.Product;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds);

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    boolean existsByIdAndDeletedAtIsNull(Long productId);

    boolean existsByIdNotAndName_ValueAndDeletedAtIsNull(Long productId, String name);

    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = :now WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId, @Param("now") ZonedDateTime now);
}
