package com.loopers.application.product;

public interface UpdateProductUseCase {

    void updateProduct(ProductUpdateCommand command);

    record ProductUpdateCommand(
            Long productId, String name, int price,
            Integer salePrice, int stock, String description
    ) {}
}
