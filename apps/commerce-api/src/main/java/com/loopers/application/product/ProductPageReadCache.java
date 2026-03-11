package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.product.ProductSortType;

import java.util.function.Supplier;

public interface ProductPageReadCache {
    PageResult<ProductReadModel> get(ProductSortType sort, int page, int size,
                                     Supplier<PageResult<ProductReadModel>> loader);
    void evictAll();
}
