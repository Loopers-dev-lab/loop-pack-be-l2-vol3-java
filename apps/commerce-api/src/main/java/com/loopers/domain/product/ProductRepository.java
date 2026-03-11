package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdWithLock(Long id);

    List<Product> findByIds(List<Long> ids);

    Product update(Product product);

    List<Product> findAll(ProductSearchCondition condition);

    void incrementLikeCount(Long id);

    void decrementLikeCount(Long id);
}
