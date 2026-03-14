package com.loopers.application.product;

import java.util.function.Supplier;

public interface ProductReadCache {
    ProductReadModel get(Long productId, Supplier<ProductReadModel> loader);
    void evict(Long productId);
    void evictAll();
}
