package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.UpdateProductUseCase.ProductUpdateCommand;

public record ProductUpdateRequest(
        String name,
        int price,
        Integer salePrice,
        int stock,
        String description
) {
    public ProductUpdateCommand toCommand(Long productId) {
        return new ProductUpdateCommand(productId, name, price, salePrice, stock, description);
    }
}
