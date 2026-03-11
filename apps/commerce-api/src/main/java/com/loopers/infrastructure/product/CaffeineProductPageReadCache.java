package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.application.product.ProductPageReadCache;
import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.ProductSortType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class CaffeineProductPageReadCache implements ProductPageReadCache {

    private final Cache<ProductPageCacheKey, PageResult<ProductReadModel>> cache;

    public CaffeineProductPageReadCache(
        @Value("${cache.product-page.maximum-size:200}") long maximumSize,
        @Value("${cache.product-page.expire-after-write-minutes:5}") long expireAfterWriteMinutes
    ) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
            .build();
    }

    @Override
    public PageResult<ProductReadModel> get(ProductSortType sort, int page, int size,
                                            Supplier<PageResult<ProductReadModel>> loader) {
        return cache.get(new ProductPageCacheKey(sort, page, size), key -> loader.get());
    }

    @Override
    public void evictAll() {
        cache.invalidateAll();
    }

    private record ProductPageCacheKey(ProductSortType sort, int page, int size) {}
}
