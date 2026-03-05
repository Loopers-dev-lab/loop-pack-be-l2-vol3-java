package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Optional<Product> findByIdWithLock(Long id);
    List<Product> findAll(ProductSortCondition condition);
    List<Product> findByBrandId(Long brandId);
    List<Product> findByIdIn(List<Long> productIds);
    void increaseLikeCount(Long id);
    void decreaseLikeCount(Long id);
}
