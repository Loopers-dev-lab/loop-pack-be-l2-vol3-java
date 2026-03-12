package com.loopers.application.product;

import java.util.Optional;

public interface ProductCacheManager {

    Optional<CachedBrandProductPage> getProductList(Long brandId, int page, int size);

    void putProductList(Long brandId, int page, int size, CachedBrandProductPage value);

    Optional<CachedProductDetail> getProductDetail(Long productId);

    void putProductDetail(Long productId, CachedProductDetail value);

    void evictProductCaches(Long productId, Long brandId);
}
