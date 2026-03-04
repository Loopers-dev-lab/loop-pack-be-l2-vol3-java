package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;

import java.time.ZonedDateTime;

public record LikedProductInfo(
        Long likeId,
        ProductInfo product,
        ZonedDateTime likedAt
) {}
