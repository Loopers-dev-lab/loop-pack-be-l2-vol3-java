package com.loopers.application.product.dto;

import java.util.UUID;

public record OrderProductInfo(
        UUID productId,
        String productName,
        int productPrice,
        UUID brandId
) {
}
