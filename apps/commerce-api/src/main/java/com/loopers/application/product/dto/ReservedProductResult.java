package com.loopers.application.product.dto;

import java.util.UUID;

public record ReservedProductResult(
        UUID productId,
        int quantity,
        String productName,
        int productPrice,
        UUID brandId
) {
}
