package com.loopers.infrastructure.brand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {

    Optional<BrandEntity> findByName(String name);

    boolean existsByName(String name);

    @Modifying
    @Query(value = "UPDATE products p SET p.deleted_at = CURRENT_TIMESTAMP WHERE p.brand_id = :brandId AND p.deleted_at IS NULL", nativeQuery = true)
    int softDeleteProductsByBrandId(@Param("brandId") Long brandId);
}
