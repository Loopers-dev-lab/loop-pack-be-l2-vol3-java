package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdWithLock(Long id);

    boolean existsById(Long id);

    Page<Product> findAll(Pageable pageable);

    Page<Product> findByBrandId(Long brandId, Pageable pageable);
}
