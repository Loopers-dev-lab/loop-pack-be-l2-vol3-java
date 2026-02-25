package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    Optional<ProductModel> findById(Long id);
    Optional<ProductModel> findByIdForUpdate(Long id);
    ProductModel save(ProductModel product);
    Page<ProductModel> findAll(Pageable pageable);
    Page<ProductModel> findAllOrderByLikesDesc(Pageable pageable);
}
