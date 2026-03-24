package com.loopers.application.product;

import com.loopers.domain.product.Product;

public record ProductInfo(Long productId, String name, String description, Integer stock, Integer price, String brand, long likeCount) {

    public static ProductInfo of(Product product, String brand) {
        return new ProductInfo(product.getId(), product.name(), product.description(), product.stock().value(), product.price().value(), brand, product.likeCount());
    }

}
