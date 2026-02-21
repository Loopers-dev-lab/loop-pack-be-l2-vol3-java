package com.loopers.infrastructure.product.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.product.Product;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> productIds);

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(Long productId);
}
