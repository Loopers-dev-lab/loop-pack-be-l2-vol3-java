package com.loopers.application.brand.dto;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.product.model.Product;

import java.util.List;

public record FindBrandResDto(Long id, String name, String description, List<ProductDto> products) {

    public record ProductDto(Long id, String name, int price, int stock, String displayStatus) {
        public static ProductDto from(Product product) {
            return new ProductDto(
                    product.getId(),
                    product.getName().value(),
                    product.getPrice().value(),
                    product.getStock().value(),
                    product.getDisplayStatus().name()
            );
        }
    }

    public static FindBrandResDto of(Brand brand, List<Product> products) {
        return new FindBrandResDto(
                brand.getId(),
                brand.getName().value(),
                brand.getDescription(),
                products.stream().map(ProductDto::from).toList()
        );
    }
}
