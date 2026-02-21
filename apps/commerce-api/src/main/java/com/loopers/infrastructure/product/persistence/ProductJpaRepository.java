package com.loopers.infrastructure.product.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.product.Product;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(Long productId);
}
