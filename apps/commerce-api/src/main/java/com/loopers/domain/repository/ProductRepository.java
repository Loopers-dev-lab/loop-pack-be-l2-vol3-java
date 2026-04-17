package com.loopers.domain.repository;

import com.loopers.domain.model.common.PageResult;
import com.loopers.domain.model.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findActiveById(Long id);

    Optional<Product> findActiveByIdWithLock(Long id);

    PageResult<Product> findAllActive(Long brandId, String sort, int page, int size);

    List<Product> findAllByBrandId(Long brandId);

    List<Product> findAllActiveByIds(List<Long> ids);

    void incrementLikeCount(Long productId);

    void decrementLikeCount(Long productId);
}
