package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAllByIdInWithLock(List<Long> ids);

    Optional<Product> findByIdIncludingDeleted(Long id);

    Page<Product> findAll(Long brandId, Pageable pageable);

}
