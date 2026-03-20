package com.loopers.application.product.cache;

import com.loopers.application.product.view.ProductView;

import java.time.Duration;
import java.util.Optional;

public interface PublicProductDetailCacheRepository {

    Optional<ProductView> findByKey(String cacheKey);

    void save(String cacheKey, ProductView value, Duration ttl);

    void evict(String cacheKey);
}
