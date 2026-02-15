package com.loopers.domain.product;

import com.loopers.domain.PageResult;

import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    PageResult<Product> findAll(Long brandId, ProductSortType sort, int page, int size);

    void softDeleteAllByBrandId(Long brandId);

    void incrementLikeCount(Long productId);

    void decrementLikeCount(Long productId);
}
