package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Optional<Product> findActiveById(Long id);
    Page<Product> findAllActive(Pageable pageable);
    Page<Product> findAllActiveByBrandId(Long brandId, Pageable pageable);
    List<Product> findAllActiveByBrandId(Long brandId);
    List<Product> findAllActiveByIdIn(List<Long> ids);
    List<Long> findAllActiveIdsByBrandId(Long brandId);
}
