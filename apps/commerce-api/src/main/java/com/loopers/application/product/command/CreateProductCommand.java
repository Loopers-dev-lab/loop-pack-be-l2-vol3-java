package com.loopers.application.product.command;
import java.util.UUID;

public record CreateProductCommand(
        String name,
        Integer price,
        Integer stock,
        String description,
        UUID categoryId,
        UUID brandId
) {
}
