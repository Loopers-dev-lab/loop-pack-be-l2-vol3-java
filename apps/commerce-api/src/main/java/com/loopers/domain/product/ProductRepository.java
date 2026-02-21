package com.loopers.domain.product;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Slice<Product> findAllBy(Pageable pageable);

    Slice<Product> findAllByBrandId(Long brandId, Pageable pageable);

    boolean existsByIdAndDeletedAtIsNull(Long productId);
}
