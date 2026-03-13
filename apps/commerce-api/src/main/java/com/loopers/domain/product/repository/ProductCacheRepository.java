package com.loopers.domain.product.repository;

import com.loopers.domain.product.model.ProductItem;

import java.util.List;
import java.util.Optional;

public interface ProductCacheRepository {

    Optional<ProductItem> get(Long productId);

    void put(Long productId, ProductItem item);

    void evict(Long productId);

    record CachedPage(List<ProductItem> items, long totalElements) {}

    Optional<CachedPage> getFirstPage();

    void putFirstPage(List<ProductItem> items, long totalElements);

    void evictFirstPage();

    Optional<Long> getLikeCount(Long productId);

    void initLikeCount(Long productId, long count);

    void incrementLikeCount(Long productId);

    void decrementLikeCount(Long productId);
}
