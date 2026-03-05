package com.loopers.application.product;

import com.loopers.domain.product.ModifyProduct;
import com.loopers.domain.product.ProductSpec;

public class ProductCommand {

    public record CreateProductCommand(
            Long brandId,
            String name,
            String thumbnailUrl,
            Long price,
            Long stock,
            String description
    ) {

        public ProductSpec toProductSpec() {
            return new ProductSpec(brandId, name, thumbnailUrl, price, stock, description);
        }
    }

    public record UpdateProductCommand(
            Long productId,
            String name,
            String thumbnailUrl,
            Long price,
            Long stock,
            String description
    ) {

        public ModifyProduct toModifyProduct() {
            return new ModifyProduct(productId, name, thumbnailUrl, price, stock, description);
        }
    }
}
