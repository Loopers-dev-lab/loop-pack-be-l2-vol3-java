package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;

public record LikeWithProduct(
    LikeModel like,
    ProductModel product
) {}
