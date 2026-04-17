package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankingItem(
        int rank,
        ProductInfo productInfo,
        Double score
) {}
