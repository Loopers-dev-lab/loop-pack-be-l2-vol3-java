package com.loopers.application.product.view;

public record ProductDetailView(
        ProductView product,
        Long rank
) {
    public static ProductDetailView from(ProductView productView, Long rank) {
        return new ProductDetailView(productView, rank);
    }
}
