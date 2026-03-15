package com.loopers.domain.product;

import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    Optional<ProductModel> findByIdWithLock(Long id);  // 비관적 락 추가
    List<ProductModel> findAllByBrandId(Long brandId);
    List<ProductModel> findAll();
    void increaseLikeCount(Long id);
    void decreaseLikeCount(Long id);
    List<ProductModel> findAll(Sort sort);
    List<ProductModel> findAllByBrandId(Long brandId, Sort sort);
}
