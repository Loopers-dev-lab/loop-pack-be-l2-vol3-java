package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankedProductInfo(
    long rank,
    double score,
    ProductInfo product
) {
}
