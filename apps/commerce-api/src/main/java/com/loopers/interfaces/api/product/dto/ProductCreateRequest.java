package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.CreateProductUseCase.ProductCreateCommand;

public record ProductCreateRequest(
        Long brandId,
        String name,
        int price,
        Integer salePrice,
        int stock,
        String description
) {
    public ProductCreateCommand toCommand() {
        return new ProductCreateCommand(brandId, name, price, salePrice, stock, description);
    }
}
