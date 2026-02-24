package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    List<Product> findAllByDeletedAtIsNull();
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.id = :id AND p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findByIdWithBrand(@Param("id") Long id);

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllWithBrand();

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllWithBrand(Sort sort);

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.brandId = :brandId AND p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllByBrandIdWithBrand(@Param("brandId") Long brandId);
}
