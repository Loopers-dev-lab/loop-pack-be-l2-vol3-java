package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

import java.util.Map;

public record ProductPageWithBrands(PageResult<Product> result, Map<Long, Brand> brandMap) {
}
