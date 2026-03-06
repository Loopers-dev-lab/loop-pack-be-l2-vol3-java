package com.loopers.domain.product;

import com.loopers.domain.product.query.ProductListCriteria;
import com.loopers.domain.product.query.ProductListQuery;
import com.loopers.domain.product.query.ProductSortOption;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    public ProductListCriteria toCriteria(ProductListQuery query) {
        ProductSortOption sortOption = ProductSortOption.fromApiValue(query.sort());
        return ProductListCriteria.of(query.brandId(), query.page(), query.size(), sortOption);
    }
}
