package com.loopers.application.like;

import java.time.ZonedDateTime;

public record LikeWithProduct(
    Long likeId,
    Long productId,
    String productName,
    int productPrice,
    ZonedDateTime likedAt
) {}
