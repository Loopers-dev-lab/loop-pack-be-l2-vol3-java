package com.loopers.application.product;

public class ProductCommand {

    public record CreateProductCommand(
            Long brandId,
            String name,
            String thumbnailUrl,
            Long price,
            Long stock,
            String description
    ) {
    }
}
