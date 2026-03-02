package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);
    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Query("SELECT p.id FROM Product p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<Long> findAllIdsByBrandIdAndDeletedAtIsNull(@Param("brandId") Long brandId);
}
