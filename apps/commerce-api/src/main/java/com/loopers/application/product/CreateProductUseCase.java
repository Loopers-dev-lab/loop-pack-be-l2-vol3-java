package com.loopers.application.product;

public interface CreateProductUseCase {

    void createProduct(ProductCreateCommand command);

    record ProductCreateCommand(
            Long brandId, String name, int price,
            Integer salePrice, int stock, String description
    ) {}
}
