package com.loopers.domain.stock;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProductStockRepository {

    ProductStock save(ProductStock productStock);

    Optional<ProductStock> findByProductId(Long productId);

    Optional<ProductStock> findByProductIdWithLock(Long productId);

    Map<Long, ProductStock> findAllByProductIds(Collection<Long> productIds);

    void deleteByProductId(Long productId);

    void deleteAllByProductIds(Collection<Long> productIds);

    void deleteAllByBrandId(Long brandId);
}
