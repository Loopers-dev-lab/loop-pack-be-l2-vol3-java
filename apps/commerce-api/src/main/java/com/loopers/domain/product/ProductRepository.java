package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    // Command
    Product save(Product product);

    // Query
    Optional<Product> findById(Long id);
    List<Product> findAllByBrandId(Long brandId);

    List<Product> findAllByIdIn(List<Long> ids);
    List<Product> findAllByIdInForUpdate(List<Long> ids);

    Page<Product> findAll(String name, Long brandId, Boolean deleted, Pageable pageable);
}
