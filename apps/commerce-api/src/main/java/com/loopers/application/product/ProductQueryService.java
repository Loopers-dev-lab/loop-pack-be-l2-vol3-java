package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.product.ProductSortType;

public interface ProductQueryService {
    ProductReadModel getById(Long id);
    PageResult<ProductReadModel> getAll(Long brandId, ProductSortType sort, int page, int size);
}
