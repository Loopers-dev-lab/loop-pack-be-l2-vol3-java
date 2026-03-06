package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.stock.ProductStock;

public record ProductWithBrandAndStock(Product product, Brand brand, ProductStock productStock) {
}
