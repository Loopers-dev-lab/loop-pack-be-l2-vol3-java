package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    boolean existsByBrandIdAndNameAndDeletedAtIsNull(Long brandId, String name);

    boolean existsByBrandIdAndNameAndIdNotAndDeletedAtIsNull(Long brandId, String name, Long id);

    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = :deletedAt WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId, @Param("deletedAt") ZonedDateTime deletedAt);
}
