package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.stock.ProductStock;

import java.util.Map;

public record ProductPageWithBrandsAndStocks(PageResult<Product> result, Map<Long, Brand> brandMap, Map<Long, ProductStock> stockMap) {
}
