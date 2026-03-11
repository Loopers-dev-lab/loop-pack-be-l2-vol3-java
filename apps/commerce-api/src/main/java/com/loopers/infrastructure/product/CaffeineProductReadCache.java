package com.loopers.infrastructure.product;

import com.github.benmanes.caffeine.cache.Cache;
import com.loopers.application.product.ProductReadCache;
import com.loopers.application.product.ProductReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@RequiredArgsConstructor
@Component
public class CaffeineProductReadCache implements ProductReadCache {

    private final Cache<Long, ProductReadModel> productDetailCache;

    @Override
    public ProductReadModel get(Long productId, Supplier<ProductReadModel> loader) {
        return productDetailCache.get(productId, key -> loader.get());
    }

    @Override
    public void evict(Long productId) {
        productDetailCache.invalidate(productId);
    }

    @Override
    public void evictAll() {
        productDetailCache.invalidateAll();
    }
}
